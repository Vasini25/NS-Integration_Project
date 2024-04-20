package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;

public class NewClient {
	
	//information for communication and to appear in the tuner
    private SocketChannel sock;
    private String token;

    //receiving and sending queues
    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;
    
    //constructor
    public NewClient(String server_ip, int server_port, int frequency, String token, BlockingQueue<Message> receivedQueue, BlockingQueue<Message> sendingQueue){
        
    	//instancing the queues and the communication data
    	this.receivedQueue = receivedQueue;
        this.sendingQueue = sendingQueue;
        this.token = token;
        
        SocketChannel sock;
        Sender sender;
        Listener listener;
        
        //try connection with server tuner
        try{
            sock = SocketChannel.open();
            sock.connect(new InetSocketAddress(server_ip, server_port));
            
            //instancing the threads it will run
            listener = new Listener( sock, receivedQueue );
            sender = new Sender( sock, sendingQueue );
            
            //setting frequency and token before starting the thread
            sender.sendConnect(frequency);
            sender.sendToken(token);
            
            //starting threads to send and receive messages
            listener.start();
            sender.start();
            
        } catch (IOException e){
            System.err.println("Failed to connect: "+e);
            System.exit(1);
        }     
    }
    
    // ===== Thread for Sending =====//
    private class Sender extends Thread {
        private BlockingQueue<Message> sendingQueue;
        private SocketChannel sock;
        
        //constructor
        public Sender(SocketChannel sock, BlockingQueue<Message> sendingQueue){
            super();
            this.sendingQueue = sendingQueue;
            this.sock = sock;      
        }

      //connecting to the server/tuner by frequency
        public void sendConnect(int frequency){
            ByteBuffer buff = ByteBuffer.allocate(4);
            buff.put((byte) 9);
            buff.put((byte) ((frequency >> 16)&0xff));
            buff.put((byte) ((frequency >> 8)&0xff));
            buff.put((byte) (frequency&0xff));
            buff.position(0);
            try{
                sock.write(buff);
            } catch(IOException e) {
                System.err.println("Failed to send HELLO" );
            }            
        }
        
        //connecting to the server/tuner by token
        public void sendToken(String token){
            byte[] tokenBytes = token.getBytes();
            ByteBuffer buff = ByteBuffer.allocate(tokenBytes.length+2);
            buff.put((byte) 10);
            buff.put((byte) tokenBytes.length);
            buff.put(tokenBytes);
            buff.position(0);
            try{
                sock.write(buff);
            } catch(IOException e) {
                System.err.println("Failed to send HELLO" );
            }  
        }
        
        
        private void senderLoop(){
            while(sock.isConnected()){
                try{
                    Message msg = sendingQueue.take();
                    if( msg.getType() == MessageType.DATA || msg.getType() == MessageType.DATA_SHORT ){
                        ByteBuffer data = msg.getData();
                        data.position(0); //reset position just to be sure
                        int length = data.capacity(); //assume capacity is also what we want to send here!
                        ByteBuffer toSend = ByteBuffer.allocate(length+2);
                        if( msg.getType() == MessageType.DATA ){
                            toSend.put((byte) 3);
                        } else { // must be DATA_SHORT due to check above
                            toSend.put((byte) 6);
                        }
                        toSend.put((byte) length);
                        toSend.put(data);
                        toSend.position(0);
                        
                        sock.write(toSend);
                    }                    
                } catch(IOException e) {
                    System.err.println("Error in socket (sender): "+e.toString());
                } catch(InterruptedException e){
                    System.err.println("Failed to take from sendingQueue: "+e.toString());
                }                
            }
        }

        //runs the thread when it starts (main of thread)
        public void run(){
            senderLoop();
        }
    }
    
    // ===== Thread for Receiving =====//
    private class Listener extends Thread {
        private BlockingQueue<Message> receivedQueue;
        private SocketChannel sock;
        
        private ByteBuffer messageBuffer = ByteBuffer.allocate(1024);
        private int messageLength = -1;
        private boolean messageReceiving = false;

        //constructor
        public Listener(SocketChannel sock, BlockingQueue<Message> receivedQueue){
            super();
            this.receivedQueue = receivedQueue;
            this.sock = sock;      
        }
        
        private void parseMessage( ByteBuffer received, int bytesReceived ){
        	MessageType typeOfMessage = null;
        	
            for( int offset=0; offset < bytesReceived; offset++ ){
			    byte byteRead = received.get(offset);
			    
			    //if the last byte was not a data or data_short message type
			    if(!messageReceiving)
			    {			    	
			    	//define the type of message received and put it in the received queue
			    	typeOfMessage = checkMessageType(byteRead);
			    	putMessageInQueue(typeOfMessage, null);
			    }
			    
			    //if last byte was data or data_short, deal with the receiving data
			    else
			    {
			    	//check first byte of the actual data that represents the length of it
			    	if (messageLength == -1) {
                        messageLength = (int) byteRead;
                        messageBuffer = ByteBuffer.allocate(messageLength);
                    } else {
                        messageBuffer.put( byteRead );
                    }
			    	
			    	//in case the byte is the last of the data previously expected
			    	if( messageBuffer.position() == messageLength ){
                        messageBuffer.position(0);
                        
                        ByteBuffer temp = ByteBuffer.allocate(messageLength);
                        temp.put(messageBuffer);
                        temp.rewind();
                        
                        putMessageInQueue(typeOfMessage, temp);
                        
                        //not receiving data bytes anymore
                        messageReceiving = false;
                    }
			    }
			}
        }
        
        //parse the token management byte
        public MessageType checkMessageType(byte byteRead)
        {
        	switch(byteRead)
        	{
        	
        	//if it is data of some kind
        	case 0x03:
        		messageLength = -1;
        		messageReceiving = true;
        		return MessageType.DATA;
        	case 0x06:
        		messageLength = -1;
        		messageReceiving = true;
        		return MessageType.DATA_SHORT;
        		
        	//if it is information about the channel
        	case 0x01:
        		return MessageType.FREE;
        	case 0x02:
        		return MessageType.BUSY;
        	case 0x04:
        		return MessageType.SENDING;
        	case 0x05:
        		return MessageType.DONE_SENDING;
        	
        	//if it is a return from the server about the token
        	case 0x0A:
        		return MessageType.TOKEN_ACCEPTED;
        	case 0x0B:
        		return MessageType.TOKEN_REJECTED;
        	case 0x09:
        		return MessageType.HELLO;
        	case 0x08:
        		return MessageType.END;
        		
        	default:
        		return MessageType.NOT_EXPECTED;
        	}
        }
        
        //Instantiate a new message and put it in the received queue
        public void putMessageInQueue(MessageType type, ByteBuffer buffer)
        {
        	if(buffer == null)
        	{
        		try {
					receivedQueue.put(new Message(type));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
        	}
        	else
        	{
				try {
					receivedQueue.put(new Message(type, buffer));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
        	}
        }
        
        //listener loop to receive Bytes
        public void receivingLoop(){
            int bytesRead = 0;
            ByteBuffer receivedBuffer = ByteBuffer.allocate(1024);
            
            //try to listen to bytes from the open socket 
            try{
                while( sock.isConnected() ){
                    bytesRead = sock.read(receivedBuffer);
                    
                    //if there is actually something to read, parse the bytes
                    if ( bytesRead > 0 ){
                        parseMessage( receivedBuffer, bytesRead );
                    } else {
                        break;
                    }
                    
                    //clear the read buffer
                    receivedBuffer.clear();              
                }
            } catch(IOException e){
                System.out.println("Error in socket (receiver): "+e.toString());
            }
        }
        
        //runs the thread when it starts (main of thread)
        public void run(){
            receivingLoop();
        }
    }
}
