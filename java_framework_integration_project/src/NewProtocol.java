import client.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class NewProtocol {
	
	// Setting the server IP, the port and the frequency numbers
    private static String SERVER_IP = "netsys.ewi.utwente.nl"; //"127.0.0.1";
    private static int SERVER_PORT = 8954;
    private static int frequency = 3951;
    
    //Also setting the token
    private String token = "java-34-1TFRGJXZBVMDS6KOI4";
    
    //the queues to be used when instantiating a node
    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;
    
    
    public NewProtocol(String server_ip, int server_port, int frequency)
    {
    	// Instantiate a new node and give it queues
    	receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();
    	new Client(SERVER_IP, SERVER_PORT, frequency, token, receivedQueue, sendingQueue);
    	
    	// Start thread to handle receiving messages
    	new receiveThread(receivedQueue).start();
    	
    }
    
    public static void main(String args[])
    {
    	new NewProtocol(SERVER_IP, SERVER_PORT, frequency);
    }
    
    
    
    
    // nested thread class to handle received messages
    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;
        
        //constructor, will also call method run()
        public receiveThread(BlockingQueue<Message> receivedQueue){
            super();
            this.receivedQueue = receivedQueue;
        }

        public void printReceivedMessage(ByteBuffer bytes, int bytesLength){
        	char[] messageArray = new char[bytesLength];
            for(int i=0; i<bytesLength; i++){
            	messageArray[i] = (char) bytes.get(i);
                System.out.print( messageArray[i]);
            }
            System.out.println();
        }

        public void run(){
            while(true) {
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
                
					default:
						System.out.println("Received something unexpected...");
						break;
                		
                	}
                } 
                catch (InterruptedException e){
                    System.err.println("Failed to take from queue: "+e);
                }                
            }
        }
    }
}
