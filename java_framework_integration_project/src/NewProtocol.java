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
    
    private byte id;
    
    
    public NewProtocol(String server_ip, int server_port, int frequency)
    {
    	// Instantiate a new node and give it queues
    	receivedQueue = new LinkedBlockingQueue<Message>();
    	receivedQueuePkt = new LinkedBlockingQueue<Packet>();
        sendingQueue = new LinkedBlockingQueue<Message>();
        
        //generate random id
        Random rand = new Random();
        id = (byte) (rand.nextInt(125) + 1);
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
        
        private boolean isHeader = true;
        private int headerPart = 1;
        
        private byte src;
        private byte dst;
        private byte size;
        private byte flag;
        private byte seqN;
        private byte ackN;
        
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
            		//check if message is for this node
            		if(dst == id || dst == 0)
    				{    					
            			System.out.print("DATA! => ");
            			printReceivedMessage(msg.getData(), this.size);
    				}
            		isHeader = true;
            		break;
            	case DATA_SHORT:
            		System.out.print("SHORT DATA! => ");
            		printReceivedMessage(msg.getData(), msg.getData().capacity());
            		if(isHeader)
            		{
            			switch(headerPart)
            			{
            			case 1:
            				this.src = msg.getData().get(0);
            				this.dst = msg.getData().get(1);
            				System.out.println(this.src + " " + this.dst);
            				headerPart++;
            				break;
            				
            			case 2:
            				this.size = msg.getData().get(0);
            				this.flag = msg.getData().get(1);
            				System.out.println((int)this.size + " " + this.flag);
            				headerPart++;
            				break;
            				
            			case 3:
            				this.seqN = msg.getData().get(0);
            				this.ackN = msg.getData().get(1);
            				System.out.println(this.seqN + " " + this.ackN);
            				
            				headerPart = 1;
            				isHeader = false;
            				break;
            			}
            		}
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
        
        public void printByteBuffer(ByteBuffer bytes, int bytesLength){
            for(int i=0; i<bytesLength; i++){
                System.out.print( Byte.toString( bytes.get(i) )+" " );
            }
            System.out.println();
        }
        
        public void SendPacket(Packet pkt)
    	{
    		try {
    			//send header
    			for(int i=0; i<=2; i++)
    			{
    				sendingQueue.put(pkt.header.getHeaderPart(i));
    			}
    			
    			//send actual message
    			sendingQueue.put(pkt.message);
    			
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
					byte dataLength = (byte) (read-2);
					
					//get destination in the last byte and convert for ascii
					byte destinationAddrr = temp.get(dataLength - 1);
					destinationAddrr -= 48;
					dataLength--;
					System.out.println(destinationAddrr);
					
					ByteBuffer dataToSend = ByteBuffer.allocate((int)dataLength);
					dataToSend.put(temp.array(), 0, (int)dataLength);
					
					MessageType MsgType = MessageType.DATA;
					if(dataLength <= 2)
					{
						MsgType = MessageType.DATA_SHORT;
					}
					
					PacketHeader pHeader = new PacketHeader((byte)id, destinationAddrr, (byte)dataLength, (byte)0, (byte)0, (byte)0);
					Message msg = new Message(MsgType, dataToSend);
					Packet pkt = new Packet(pHeader, msg);
					SendPacket(pkt);
					
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
    	private byte flags;
    	private byte seqNumber;
    	private byte ackNumber;
    	
		private byte[] shortDataHeaderPieces = new byte[2];
		
		private Message[] headerPieces = new Message[3];
    	
    	public PacketHeader(byte sourceAddress, byte destinationAddress, byte payloadSize, byte flags, byte seqNumber, byte ackNumber)
    	{
    		this.sourceAddress = sourceAddress;
    		this.destinationAddress = destinationAddress;
    		this.payloadSize = payloadSize;
    		this.flags = flags;
    		this.seqNumber = seqNumber;
    		this.ackNumber = ackNumber;
    		
    		createHeader();
    	}
    	
    	private void createHeader()
    	{	
    		//addresses part
    		shortDataHeaderPieces[0] = sourceAddress;
    		shortDataHeaderPieces[1] = destinationAddress;
    		ByteBuffer addresses = ByteBuffer.allocate(2);
    		addresses.put(shortDataHeaderPieces);
    		this.headerPieces[0] = new Message(MessageType.DATA_SHORT, addresses);
    		
    		//type and size of payload part
    		shortDataHeaderPieces[0] = payloadSize;
    		shortDataHeaderPieces[1] = flags;
    		ByteBuffer sizeAndFlags = ByteBuffer.allocate(2);
    		sizeAndFlags.put(shortDataHeaderPieces);
    		this.headerPieces[1] = new Message(MessageType.DATA_SHORT, sizeAndFlags);
    		
    		//seqNumber and ackNumber part
    		shortDataHeaderPieces[0] = seqNumber;
    		shortDataHeaderPieces[1] = ackNumber;
    		ByteBuffer seqAndAck = ByteBuffer.allocate(2);
    		seqAndAck.put(shortDataHeaderPieces);
    		this.headerPieces[2] = new Message(MessageType.DATA_SHORT, seqAndAck);
    		
    		// to send message = sendingQueue.put(messageToSend);  messageToSend is of type Message
        	// Message msg = new Message(MsgType, dataToSend);  ByteBuffer dataToSend = ByteBuffer.allocate(dataLength); dataToSend.put(temp.array(), 0, dataLength);
    	}
    	public Message[] getHeaderAsMessages()
    	{
    		return headerPieces;
    	}
    	
    	public Message getHeaderPart(int index)
    	{
    		return headerPieces[index];
    	}
    	
    	public ByteBuffer getInfo(Message msg)
    	{
    		return msg.getData();
    	}
    	
    	public byte getFlags()
    	{
    		return flags;
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
