import client.Client;
import client.Message;
import client.MessageType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * Made by:
 * Dylan Sterling -S3177858.
 * Ahmet Ertugrul Hacioglu -S3362264.
 *
 */

public class MyProtocol {

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8955;
    // The frequency to use.
    private static int frequency = 3981;

    private int id = (int) (Math.random() * 254) + 1;

    private List<Node> nodeslist = Collections.synchronizedList(new ArrayList<>());

    private Message nextMessage = null;

    private MessageType lastMessage;

    private String lastReceivedMessage = "";

    private int nextMessageId = 0;

    private int newAck = 0;

    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/
    // The token you received for your frequency range
    String token = "java-34-1TFRGJXZBVMDS6KOI4";


    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    public MyProtocol(String server_ip, int server_port, int frequency) {
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        new Client(SERVER_IP, SERVER_PORT, frequency, token, receivedQueue,
                   sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        new keepAliveThread().start();

        nodeslist.add(new Node(id));


        // handle sending from stdin from this thread.
        try {
            System.out.println("Welcome to the underwater chat, you are node: " + id);
            System.out.println("To send a message to all nearby nodes, simply type your message");
            System.out.println(
                    "If you want to send a message to a specific connected node type: ~message " +
                            "[nodenumber], press enter and start writing your message");
            System.out.println("To see which nodes are connected type ~connected");
            ByteBuffer temp = ByteBuffer.allocate(1024);
            int read = 0;
            int new_line_offset = 0;
            while (true) {
                read = System.in.read(temp.array()); // Get data from stdin, hit enter to send!
                if (read > 0) {
                    if (temp.get(0) == '~') {
                        if (temp.get(read - 1) == '\n' || temp.get(read - 1) == '\r') {
                            new_line_offset =
                                    1;//Check if last char is a return or newline so we can strip it
                        }
                        if (read > 1 &&
                                (temp.get(read - 2) == '\n' || temp.get(read - 2) == '\r')) {
                            new_line_offset =
                                    2; //Check if second to last char is a return or newline so we can strip it
                        }

                        String command = "";
                        for (int i = 1; i < read - new_line_offset; i++) {
                            command += (char) temp.get(i);
                        }
                        String[] commands = command.split(" ");
                        if (command.equals("connected")) {
                            System.out.println("Connected nodes are: ");

                            for (Node node : nodeslist) {
                                if (node.getId() != id) {
                                    System.out.println(
                                            node.getId() + ", with nexthop: " + node.getNextHop() +
                                                    ", with a lastheard value of: " +
                                                    node.getLastHeard());
                                }
                            }

                        } else if (commands[0].equals(
                                "message")) { // for sending a message to a specific user
                            nextMessageId = Integer.parseInt(commands[1]);
                            System.out.println("sending next message to: " + commands[1]);
                        } else {
                            System.out.println("invalid command");
                        }

                    } else {

                        if (temp.get(read - 1) == '\n' || temp.get(read - 1) == '\r') {
                            new_line_offset =
                                    1;//Check if last char is a return or newline so we can strip it
                        }
                        if (read > 1 &&
                                (temp.get(read - 2) == '\n' || temp.get(read - 2) == '\r')) {
                            new_line_offset =
                                    2; //Check if second to last char is a return or newline so we can strip it
                        }
                        ByteBuffer toSend = ByteBuffer.allocate(
                                read - new_line_offset + 1); // copy data without newline / returns
                        toSend.put((byte) id);
                        toSend.put(temp.array(), 0,
                                   read - new_line_offset); // enter data without newline / returns

                        Message msg;
                        int nextHop = 0;

                        for (Node node : nodeslist) {
                            if (node.getId() == nextMessageId) {
                                nextHop = node.getNextHop();
                                break;
                            }
                        }

                        if (nextMessageId != 0 && nextHop == 0) {
                            System.out.println("This node is not known!");
                            nextMessageId = 0;
                        } else {
                            if (nextMessageId != 0) {
                                int cooldown = (int) (Math.random() * 50 + 100);
                                msg = createDataMessage(toSend, (byte) 0, nextHop, nextMessageId);
                                nextMessageId = 0;

                                sendingQueue.put(msg);

                                boolean acknowledged = false;
                                while (!acknowledged) {
                                    if (newAck == Byte.toUnsignedInt(msg.getData().get(3))) {
                                        System.out.println("message has been acknowledged");
                                        newAck = 0;
                                        acknowledged = true;
                                    }
                                    if (cooldown <= 0 && lastMessage == MessageType.FREE) {
                                        sendingQueue.put(msg);
                                        cooldown = (int) (Math.random() * 50 + 100);
                                        //System.out.println(cooldown);
                                    } else if (cooldown <= 0) {
                                        cooldown = 100;
                                    }
                                    if (cooldown >= 0) {
                                        cooldown--;
                                        Thread.sleep(100);
                                    }
                                }
                            } else {
                                List<Integer> a = new ArrayList<>();

                                for (Node node : nodeslist) {
                                    if (node.getId() == node.getNextHop() ||
                                            node.getNextHop() == 0) {
                                        a.add(node.getId());
                                    }
                                }

                                int[] b = new int[a.size()];
                                for (int i = 0; i < a.size(); i++) {
                                    b[i] = a.get(i);
                                }
                                msg = createBroadcast(toSend, b, 0);
                                if (lastMessage == MessageType.FREE) {
                                    sendingQueue.put(msg);
                                } else {
                                    nextMessage = msg;
                                }
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException | IOException e) {
            System.exit(2);
        }
    }

    public static void main(String args[]) {
        if (args.length > 0) {
            frequency = Integer.parseInt(args[0]);
        }
        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);
    }

    public Message createDataMessage(ByteBuffer data, byte flags, int nextHop, int receiver) {
        Message result;
        ByteBuffer toSend = ByteBuffer.allocate(data.capacity() + 5);

        toSend.put((byte) id);
        toSend.put(flags);
        toSend.put((byte) nextHop);
        toSend.put((byte) receiver);
        toSend.put((byte) data.capacity());
        toSend.put(data.array(), 0, data.capacity());

        result = new Message(MessageType.DATA, toSend);

        return result;
    }

    public Message createBroadcast(ByteBuffer data, int[] oldNodes, int nextNode) {
        Message result;
        ByteBuffer toSend = ByteBuffer.allocate(data.capacity() + 6 + oldNodes.length);

        toSend.put((byte) id);
        toSend.put((byte) 0b00010000);
        toSend.put((byte) nextNode);
        toSend.put((byte) 0);
        toSend.put((byte) data.capacity());
        toSend.put((byte) oldNodes.length);
        for (int oldNode : oldNodes) {
            //System.out.println(oldNode);
            toSend.put((byte) oldNode);
        }
        toSend.put(data.array(), 0, data.capacity());

        result = new Message(MessageType.DATA, toSend);
        return result;
    }

    public Message createAck(int id) {
        ByteBuffer toSend = ByteBuffer.allocate(2);
        toSend.put((byte) id);
        toSend.put((byte) 0b01000000);
        Message result = new Message(MessageType.DATA_SHORT, toSend);

        return result;
    }

    public Message createListMessage(int receiver) {
        int read = 0;

        for (Node ignored : nodeslist) {
            read += 2;
        }


        ByteBuffer data = ByteBuffer.allocate(read + 1);

        data.put((byte) id);
        for (Node node : nodeslist) {
            data.put((byte) node.getId());
            data.put((byte) node.getNextHop());
        }


        return createDataMessage(data, (byte) 0b10000000, receiver, receiver);
    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue) {
            super();
            this.receivedQueue = receivedQueue;
        }

        public void run() {
            while (true) {
                try {
                    Message m = receivedQueue.take();

                    if (m.getType() == MessageType.BUSY) {
                        lastMessage = MessageType.BUSY;
                    } else if (m.getType() == MessageType.FREE) {
                        lastMessage = MessageType.FREE;
                        if (nextMessage != null) {
                            sendingQueue.put(nextMessage);
                            nextMessage = null;
                        }
                    } else if (m.getType() == MessageType.DATA) {
                        int senderId = m.getId();
                        byte flags = m.getFlags();
                        boolean newNode = true;

                        if (senderId == id) {
                            id = (int) (Math.random() * 254) + 1;
                            System.out.println(
                                    "your node number got taken by another node, your new id is: " +
                                            id);
                            nodeslist.set(0, new Node(id));
                        }

                        for (Node node : nodeslist) {
                            if (node.getId() == senderId) {
                                node.setLastHeard(5);
                                nodeslist.set(nodeslist.indexOf(node), node);
                                newNode = false;
                                if (node.getNextHop() != senderId) {
                                    nodeslist.remove(node);
                                    newNode = true;
                                }
                                break;
                            }
                        }
                        if (newNode) {
                            Node node = new Node(senderId);
                            node.setNextHop(senderId);
                            node.setLastHeard(5);
                            nodeslist.add(node);
                        }

                        if (flags == 0b00000000 || flags == 0b01000000) {
                            if (Byte.toUnsignedInt(m.getData().get(2)) == id) {
                                if (Byte.toUnsignedInt(m.getData().get(3)) == id) {
                                    int messageLength = m.getData().get(4);
                                    String message = "";
                                    for (int i = 6; i < messageLength + 5; i++) {
                                        message += (char) m.getData().get(i);
                                    }
                                    if (flags == 0b00000000) {
                                        if (!message.equals(lastReceivedMessage)) {
                                            System.out.println("Received message: " + message +
                                                                       ", from node " +
                                                                       Byte.toUnsignedInt(
                                                                               m.getData().get(5)));
                                            lastReceivedMessage = message;
                                        }
                                        int nextHop = 0;

                                        for (Node node : nodeslist) {
                                            if (node.getId() ==
                                                    Byte.toUnsignedInt(m.getData().get(5))) {
                                                nextHop = node.getNextHop();
                                            }
                                        }

                                        ByteBuffer ack = ByteBuffer.allocate(1);
                                        ack.put((byte) id);
                                        Message msg =
                                                createDataMessage(ack, (byte) 0b01000000, nextHop,
                                                                  Byte.toUnsignedInt(
                                                                          m.getData().get(5)));
                                        nextMessage = msg;
                                    } else {
                                        newAck = Byte.toUnsignedInt(m.getData().get(5));
                                    }
                                } else {
                                    int messageLength = m.getData().get(4);
                                    ByteBuffer message = ByteBuffer.allocate(m.getData().get(4));
                                    for (int i = 5; i < messageLength + 5; i++) {
                                        message.put(m.getData().get(i));
                                    }
                                    int nextHop = 0;

                                    for (Node node : nodeslist) {
                                        if (node.getId() ==
                                                Byte.toUnsignedInt(m.getData().get(3))) {
                                            nextHop = node.getNextHop();
                                        }
                                    }

                                    Message msg = createDataMessage(message, flags, nextHop,
                                                                    Byte.toUnsignedInt(
                                                                            m.getData().get(3)));
                                    nextMessage = msg;
                                }
                            }
                        } else if ((flags & 0b11111111) == 0b10000000) {
                            if (Byte.toUnsignedInt(m.getData().get(2)) == id) {
                                Message msg = createAck(senderId);
                                nextMessage = msg;
                            }
                            for (int i = 6; i < m.getData().get(4) + 5; i += 2) {
                                int node = Byte.toUnsignedInt(m.getData().get(i));
                                boolean inList = false;

                                for (Node node1 : nodeslist) {
                                    if (node1.getId() == node) {
                                        inList = true;
                                        break;
                                    }
                                }
                                if (!inList && Byte.toUnsignedInt(m.getData().get(i + 1)) != id) {
                                    Node node1 = new Node(node);
                                    node1.setNextHop(senderId);
                                    nodeslist.add(node1);

                                }
                            }
                            List<Node> removeList = new ArrayList<>();
                            for (Node node : nodeslist) {
                                if (node.getNextHop() == senderId) {
                                    boolean contains = false;
                                    for (int i = 6; i < m.getData().get(4) + 5; i += 2) {
                                        int node1 = Byte.toUnsignedInt(m.getData().get(i));

                                        if (node.getId() == node1) {
                                            contains = true;
                                        }
                                    }
                                    if (!contains) {
                                        removeList.add(node);
                                    }
                                }

                                for (Node node1 : nodeslist) {
                                    if (node.getNextHop() == node1.getId()) {
                                        if (node1.getLastHeard() == 0) {
                                            removeList.add(node);
                                        }
                                    }
                                }

                            }
                            for (Node node : removeList) {
                                nodeslist.remove(node);
                            }
                        } else if (flags == 0b00010000) {
                            if (m.getData().get(2) == 0 ||
                                    Byte.toUnsignedInt(m.getData().get(2)) == id) {
                                boolean process = false;

                                for (Node node : nodeslist) {
                                    if (node.getId() == Byte.toUnsignedInt(m.getData().get(6))) {
                                        if (node.getNextHop() == senderId) {
                                            process = true;
                                        }
                                    }
                                }

                                if (m.getData().get(2) != 0) {
                                    Message msg = createAck(senderId);
                                    sendingQueue.put(msg);
                                }
                                if (process) {
                                    int messageLength = m.getData().get(4);
                                    String message = "";
                                    for (int i = 7 + m.getData().get(5);
                                         i < messageLength + 6 + m.getData().get(5); i++) {
                                        message += (char) m.getData().get(i);
                                    }
                                    List<Integer> nodes = new ArrayList<>();
                                    for (int i = 6; i < 6 + m.getData().get(5); i++) {
                                        nodes.add(Byte.toUnsignedInt(m.getData().get(i)));
                                    }
                                    if (!message.equals(lastReceivedMessage)) {
                                        System.out.println("received new broadcast: " + message +
                                                                   ", from node " +
                                                                   Byte.toUnsignedInt(
                                                                           m.getData().get(6)));
                                        lastReceivedMessage = message;
                                    }

                                    if (nodes.size() < nodeslist.size()) {
                                        for (Node node : nodeslist) {
                                            if (!nodes.contains(node.getId()) &&
                                                    node.getNextHop() == node.getId()) {
                                                nodes.add(node.getId());
                                                int[] b = new int[nodes.size()];
                                                for (int i = 0; i < nodes.size(); i++) {
                                                    b[i] = nodes.get(i);
                                                }
                                                ByteBuffer toSend =
                                                        ByteBuffer.allocate(message.length() + 1);
                                                toSend.put(m.getData().get(6));
                                                toSend.put(message.getBytes());
                                                Message msg =
                                                        createBroadcast(toSend, b, node.getId());
                                                nextMessage = msg;

                                                int cooldown = (int) (Math.random() * 100) + 50;
                                                boolean acknowledged = false;
                                                while (!acknowledged) {
                                                    if (!receivedQueue.isEmpty()) {
                                                        Message response = receivedQueue.take();
                                                        if (response.getType() ==
                                                                MessageType.DATA_SHORT &&
                                                                response.getFlags() == 0b01000000 &&
                                                                response.getId() == id) {
                                                            acknowledged = true;
                                                        } else if (response.getType() ==
                                                                MessageType.FREE) {
                                                            lastMessage = MessageType.FREE;
                                                            if (nextMessage != null) {
                                                                sendingQueue.put(nextMessage);
                                                                nextMessage = null;
                                                            }
                                                        } else if (response.getType() ==
                                                                MessageType.BUSY) {
                                                            lastMessage = MessageType.BUSY;
                                                        }
                                                    }
                                                    if (cooldown <= 0 &&
                                                            lastMessage == MessageType.FREE) {
                                                        sendingQueue.put(msg);
                                                        cooldown = (int) (Math.random() * 200) + 50;
                                                        //System.out.println(cooldown);
                                                    }
                                                    if (cooldown > 0) {
                                                        cooldown--;
                                                        Thread.sleep(100);
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    /*} else if (m.getType() == MessageType.DATA_SHORT) {
                    } else if (m.getType() == MessageType.DONE_SENDING){
                    } else if (m.getType() == MessageType.HELLO){
                    } else if (m.getType() == MessageType.SENDING){*/
                    } else if (m.getType() == MessageType.END) {
                        System.out.println("Connection was terminated");
                        System.exit(0);
                    } else if (m.getType() == MessageType.TOKEN_ACCEPTED) {
                        System.out.println("connected to server");
                    } else if (m.getType() == MessageType.TOKEN_REJECTED) {
                        System.out.println("There was an error with connecting to the server");
                    }
                } catch (InterruptedException e) {
                    System.err.println("Failed to take from queue: " + e);
                }
            }
        }
    }

    public class keepAliveThread extends Thread {
        public void run() {
            int cooldown = (int) (Math.random() * 10000 + 15000);
            while (true) {
                List<Node> removeList = new ArrayList<>();
                for (Node node : nodeslist) {
                    if (node.getNextHop() == node.getId()) {
                        if (node.getLastHeard() <= 0) {
                            removeList.add(node);
                        } else {
                            node.setLastHeard(node.getLastHeard() - 1);
                        }
                    }
                    boolean contains = false;
                    for (Node node1 : nodeslist) {
                        if (node.getNextHop() == node1.getId() || node.getId() == id) {
                            contains = true;
                            break;
                        }
                    }
                    if (!contains) {
                        removeList.add(node);
                    }
                }
                for (Node node : removeList) {
                    nodeslist.remove(node);
                }
                if (lastMessage == MessageType.FREE || lastMessage == MessageType.TOKEN_ACCEPTED ||
                        lastMessage == null) {
                    Message msg = createListMessage(0);
                    try {
                        sendingQueue.put(msg);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                try {
                    Thread.sleep(cooldown);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                cooldown = (int) (Math.random() * 10000 + 15000);
            }
        }
    }
}

