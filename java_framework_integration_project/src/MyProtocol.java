import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.crypto.dsig.spec.XSLTTransformParameterSpec;

/**
* This is just some example code to show you how to interact 
* with the server using the provided client and two queues.
* Feel free to modify this code in any way you like!
*/

public class MyProtocol{

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 3950;

    boolean waitingForAck;
    
    // View the simulator at https://netsys.ewi.utwente.nl/integrationproject/
    // The token you received for your frequency range
    String token = "java-34-1TFRGJXZBVMDS6KOI4";

    int id = (int) (Math.random() * 255);

    int cooldown = 0;
    List<Integer> nodes = new ArrayList<>();

    MessageType lastType;
    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;

    public MyProtocol(String server_ip, int server_port, int frequency){
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        new Client(SERVER_IP, SERVER_PORT, frequency, token, receivedQueue, sendingQueue); // Give the client the Queues to use

        nodes.add(id);

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!



        // handle sending from stdin from this thread.
        try{
            ByteBuffer temp = ByteBuffer.allocate(1024);
            int read = 0;
            int new_line_offset = 0;
            while(true){
                /*if(nodes.size() == 1 && cooldown == 0){
                    ByteBuffer toSend = ByteBuffer.allocate(2); // copy data without newline / returns
                    toSend.put((byte) id);
                    toSend.put((byte) 8);
                    Message msg;
                    msg = new Message(MessageType.DATA_SHORT, toSend);
                    sendingQueue.put(msg);
                    cooldown = 300;
                }*/
                cooldown--;
                read = System.in.read(temp.array()); // Get data from stdin, hit enter to send!
                if(read > 0) {

                    if (temp.get(read - 1) == '\n' || temp.get(read - 1) == '\r')
                        new_line_offset = 1; //Check if last char is a return or newline so we can strip it
                    if (read > 1 && (temp.get(read - 2) == '\n' || temp.get(read - 2) == '\r'))
                        new_line_offset = 2; //Check if second to last char is a return or newline so we can strip it
                    if (temp.get(0) == '~') {
                        String command = "";
                        for(int i = 1; i < read - new_line_offset ; i ++){
                            command += (char)temp.get(i);
                        }
                        if(command.equals("shownodes")){
                            System.out.println("Currently know nodes: " + nodes);
                        } else if (command.equals("asktable")){
                            ByteBuffer toSend = ByteBuffer.allocate(read - new_line_offset + 2);
                            toSend.put((byte) id);
                            toSend.put((byte) 0b00001000);
                            Message msg = new Message(MessageType.DATA_SHORT, toSend);
                            sendingQueue.put(msg);
                        }else{
                            System.out.println( command + " is an invalid command");
                        }
                    } else {
                        ByteBuffer toSend = ByteBuffer.allocate(read - new_line_offset + 2); // copy data without newline / returns
                        toSend.put((byte) id);
                        toSend.put((byte) 0);
                        toSend.put((byte) 0b00000001); // code for sending text
                        toSend.put((byte) (read - new_line_offset + 4));
                        toSend.put(temp.array(), 0, read - new_line_offset); // enter data without newline / returns
                        Message msg;
                        if ((read - new_line_offset) > 2) {
                            msg = new Message(MessageType.DATA, toSend);
                        } else {
                            msg = new Message(MessageType.DATA_SHORT, toSend);
                        }
                        sendingQueue.put(msg);
                    }
                }
            }
        } catch (InterruptedException e){
            System.exit(2);
        } catch (IOException e){
            System.exit(2);
        }        
    }

    public static void main(String args[]) {
        if(args.length > 0){
            frequency = Integer.parseInt(args[0]);
        }
        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);        
    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue){
            super();
            this.receivedQueue = receivedQueue;
        }

        public void printByteBuffer(ByteBuffer bytes, int bytesLength){
            for(int i=0; i<bytesLength; i++){
                System.out.print( Byte.toString( bytes.get(i) )+" " );
            }
            System.out.println();
        }

        public void run(){
            while(true) {
                try{
                    Message m = receivedQueue.take();
                    if(m.getData() != null && !nodes.contains((int)m.getData().get(0))){
                        nodes.add(Byte.toUnsignedInt(m.getData().get(0)));
                    }
                    //System.out.println("known nodes: " + nodes);
                    if (m.getType() == MessageType.BUSY){
                        lastType = MessageType.BUSY;
                        System.out.println("BUSY");
                    } else if (m.getType() == MessageType.FREE){
                        lastType = MessageType.FREE;
                        System.out.println("FREE");
                    } else if (m.getType() == MessageType.DATA){
                        lastType = MessageType.DATA;
                        System.out.print("DATA: ");
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data
                        int length = m.getData().get(3);
                        String text = "";
                        for(int i = 4; i < length; i++){
                            text += (char) m.getData().get(i);
                        }
                        System.out.println("which gives message : " + text + " - from node " + Byte.toUnsignedInt(m.getData().get(0)));

                        if(m.getData().get(2) == (byte) 0b00100000){
                            if((char)m.getData().get(1) == id){
                                for(int i = 4; i < length; i++){
                                    if(!nodes.contains((int) m.getData().get(i))) {
                                        nodes.add((int) m.getData().get(i));
                                    }
                                }
                            }
                            ByteBuffer toSend = ByteBuffer.allocate(2); // copy data without newline / returns
                            toSend.put((byte) id);
                            toSend.put((byte) 0b10000000);
                            Message msg = new Message(MessageType.DATA_SHORT, toSend);
                            sendingQueue.put(msg);
                        }

                    } else if (m.getType() == MessageType.DATA_SHORT){
                        lastType = MessageType.DATA_SHORT;
                        System.out.print("DATA_SHORT: ");
                        printByteBuffer( m.getData(), m.getData().capacity() ); //Just print the data

                        if(m.getData().get(1) == 0b00001000){
                            System.out.println("received request for node list");
                            String text = "";
                            for(int id : nodes){
                                text += id;
                            }
                            ByteBuffer toSend = ByteBuffer.allocate(text.length() + 4); // copy data without newline / returns
                            toSend.put((byte) id);
                            toSend.put(m.getData().get(0));
                            toSend.put((byte) 0b00100000);
                            toSend.put((byte) (text.length() + 4));
                            toSend.put(text.getBytes());
                            Message msg = new Message(MessageType.DATA, toSend);
                            sendingQueue.put(msg);
                            boolean received = false;
                            while (!received){
                                Thread.sleep((long) (Math.random() * 10000 + 2000));
                                System.out.println("checking if we have received an ack");
                                Message response = receivedQueue.take();
                                if (response.getType() == MessageType.DATA_SHORT) {
                                    if (response.getData().get(1) == (byte) 0b10000000) {
                                        received = true;
                                    }
                                } else if (response.getType().equals(MessageType.FREE)){
                                    lastType = MessageType.FREE;

                                } else if (response.getType().equals(MessageType.BUSY)){
                                    lastType = MessageType.BUSY;
                                }else {
                                    if(lastType.equals(MessageType.FREE)) {
                                        sendingQueue.put(msg);
                                        System.out.println("resending message");
                                    }
                                }
                            }

                        }


                    } else if (m.getType() == MessageType.DONE_SENDING){
                        lastType = MessageType.DONE_SENDING;
                        System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO){
                        lastType = MessageType.HELLO;
                        System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING){
                        lastType = MessageType.SENDING;
                        System.out.println("SENDING");
                    } else if (m.getType() == MessageType.END){
                        lastType = MessageType.END;
                        System.out.println("END");
                        System.exit(0);
                    } else if (m.getType() == MessageType.TOKEN_ACCEPTED){
                        lastType = MessageType.TOKEN_ACCEPTED;
                        System.out.println("Token Valid!");
                    } else if (m.getType() == MessageType.TOKEN_REJECTED){
                        lastType = MessageType.TOKEN_REJECTED;
                        System.out.println("Token Rejected!");
                    }
                } catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }                
            }
        }
    }
}

