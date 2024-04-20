import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.midi.SysexMessage;

public class NewProtocol {
	
	// Setting the server IP, the port and the frequency numbers
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    private static int SERVER_PORT = 8954;
    private static int frequency = 3951;
    
    //Also setting the token
    private String token = "java-34-1TFRGJXZBVMDS6KOI4";
    
    //the queues to be used when instantiating a node
    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Packet> receivedQueuePkt;
    private BlockingQueue<Message> sendingQueue;
    
    private char id;
    
    
    public NewProtocol(String server_ip, int server_port, int frequency)
    {
    	// Instantiate a new node and give it queues
    	receivedQueue = new LinkedBlockingQueue<Message>();
    	receivedQueuePkt = new LinkedBlockingQueue<Packet>();
        sendingQueue = new LinkedBlockingQueue<Message>();
        
        //generate random id
        Random rand = new Random();
        id = (char) (rand.nextInt(125) + 1);
        System.out.println((int) id);
        
    	new Client(SERVER_IP, SERVER_PORT, frequency, token, receivedQueue, sendingQueue);
    	
    	// Start thread to handle receiving messages
    	new receiveThread(receivedQueue, receivedQueuePkt).start();
    	
    	//Start thread to handle sending messages
    	new sendThread(sendingQueue).start();
    	
    }
    
    public static void main(String args[])
    {
    	new NewProtocol(SERVER_IP, SERVER_PORT, frequency);
    }
    
    
    //==========   THREADS   ============//
    
    //===   receive   ===//
    
    // nested thread class to handle received messages
    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;
        private BlockingQueue<Packet> receivedQueuePkt;
        
        //constructor, will also call method run()
        public receiveThread(BlockingQueue<Message> receivedQueue, BlockingQueue<Packet> receivedQueuePkt){
            super();
            this.receivedQueue = receivedQueue;
            this.receivedQueuePkt = receivedQueuePkt;
        }
        
        //method called when data message is received
        //it casts the data from bytes to char and then print it
        public void printReceivedMessage(ByteBuffer bytes, int bytesLength){
        	
        	//char array to be used to cast bytes to char
        	char[] messageArray = new char[bytesLength];
        	
        	//cast each byte to a char in the array and print it
            for(int i=0; i<bytesLength; i++){
            	messageArray[i] = (char) bytes.get(i);
                System.out.print( messageArray[i]);
            }
            System.out.println();
        }
        
        public void receivePackets()
        {
        	try {
        		Packet pkt = receivedQueuePkt.take();
        		switch(pkt.getHeader().getPacketType())
        		{
        		case ACK:
        			System.out.println("PKT ACK");
        			break;
        			
        		default: 
        			System.out.println("Packet received" + pkt.getHeader().getPacketType());
        			receiveMessage();
        			break;
        		}
        	} catch(InterruptedException e) {
        		System.exit(1);
        	}
        }
        
        //try/catch to receive messages
        public void receiveMessage()
        {
        	try{
            	Message msg = receivedQueue.take();
            	switch(msg.getType())
            	{
            	//managing with tokens when connecting, just useful the first time
            	case TOKEN_ACCEPTED:
            		System.out.println("Valid Token!");
            		break;
            	case TOKEN_REJECTED:
            		System.out.println("Token Rejected!");
            		break;
            		
            	//managing start and end of communications
            	case HELLO:
            		System.out.println("HELLO !");
            		break;
            	case ACK:
            		System.out.println("ACK");
            		break;
            	case END:
            		System.out.println("END");
            		System.exit(0);
            		
            	//managing receiving data and short data
            	case DATA:
            		System.out.print("DATA! => ");
            		printReceivedMessage(msg.getData(), msg.getData().capacity());
            		
            		break;
            	case DATA_SHORT:
            		System.out.print("SHORT DATA! => ");
            		printReceivedMessage(msg.getData(), msg.getData().capacity());
            		break;
            	
            	//to manage received messages about the channel/buffer
            	case FREE:
            		System.out.println("Free");
            		break;
            	case BUSY:
            		System.out.println("Busy!");
            		break;
            	
            	// cannot receive data while sending it 
            	case SENDING:
            		System.out.println("Sending data...");
            		break;
            	case DONE_SENDING:
            		System.out.println("ALL data has been SENT !!!");
            		break;
            		
				default:
					System.out.println(msg.getType());
					break;
            	}
            } 
            catch (InterruptedException e){
                System.err.println("Failed to take from queue: "+e);
            }
        }

        //basically a listener
        public void run(){
            while(true) {
                receiveMessage();
            }
        }
    }
    
    //===   send   ===//
    
    // nested thread class to handle sending messages
    private class sendThread extends Thread{
    	private BlockingQueue<Message> sendingQueue;
    	
    	//constructor, will also call method run()
        public sendThread(BlockingQueue<Message> sendingQueue){
            super();
            this.sendingQueue = sendingQueue;
        }
        
        public void sendMessage(Message messageToSend)
        {
			try {
				sendingQueue.put(messageToSend);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
        
        public void run(){
        	
        	//constantly try to get an input and 
        	while(true){
        		ByteBuffer temp = ByteBuffer.allocate(1024);
        		int read = 0;
                try {
					read = (char)System.in.read(temp.array());
					//read = (char)System.in.read();
					int dataLength = read-2;
					
					ByteBuffer dataToSend = ByteBuffer.allocate(dataLength);
					dataToSend.put(temp.array(), 0, dataLength);
					
					/*for(int i=0; i<dataLength; i++)
					{
						System.out.println(dataToSend.get(i));
					}*/
					
					MessageType MsgType = MessageType.DATA;
					if(dataLength <= 2)
					{
						MsgType = MessageType.DATA_SHORT;
					}

					Message msg = new Message(MsgType, dataToSend);
					sendMessage(msg);
					
					Message ack = new Message(MessageType.ACK);
					
				} catch (IOException e) {
					System.out.println("Failed to read from input: "+e);
					System.exit(1);
				}
        	}
        }
    }
    
    
    //========== PACKET ==========//
    private class Packet {
    	private PacketHeader header;
    	private Message message;
    	
    	public Packet(PacketHeader header)
    	{
    		this.header = header;
    	}
    	
    	public Packet(PacketHeader header, Message message)
    	{
    		this.header = header;
    		this.message = message;
    	}
    	
    	public PacketHeader getHeader()
    	{
    		return header;
    	}
    	
    	public Message getPayload()
    	{
    		return message;
    	}
    }
    
    
    public class PacketHeader {
    	private byte sourceAddress;
    	private byte destinationAddress = 0x00;
    	private byte payloadSize;
    	private PacketType type;
    	private byte seqNumber;
    	private byte ackNumber;
    	
    	public PacketHeader(byte sourceAddress, byte destinationAddress, byte payloadSize, PacketType type, byte seqNumber, byte ackNumber)
    	{
    		this.sourceAddress = sourceAddress;
    		this.destinationAddress = destinationAddress;
    		this.type = type;
    		this.payloadSize = payloadSize;
    		this.seqNumber = seqNumber;
    		this.ackNumber = ackNumber;
    	}
    	
    	public PacketType getPacketType()
    	{
    		return type;
    	}
    	
    	public byte getPayloadSize()
    	{
    		return payloadSize;
    	}
    	
    	public byte getackNumber()
    	{
    		return ackNumber;
    	}
    	
    	public byte getSeqNumeber()
    	{
    		return seqNumber;
    	}
    	
    	public byte getSrcAddress()
    	{
    		return sourceAddress;
    	}
    	
    	public byte getDstAddress()
    	{
    		return destinationAddress;
    	}
    }
    
    public enum PacketType {
    	DATA,
    	ACK
    }
}
