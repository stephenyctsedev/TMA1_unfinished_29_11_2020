/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
//package posserver;

/**
 *
 * @author mfng
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;

public class POSServer{
	
	//Define the constants
	private final static int MAX_NO_OF_CONNECTION = 5;
	private final static String ITEMCODE_FILE_PATH = "D:\\TMA1_unfinished_29_11_2020\\itemCode.csv";
	private final static String TRANS_HIST_FILE_PATH = "D:\\TMA1_unfinished_29_11_2020\\transaction.csv";
	
	public static void main(String args[]){
		try{
			//1. Declare variables
			/* 1a. Server socket */
			ServerSocket serverSocket;			
			SharedSocketPool pool;/* Shared object */
			
			//2. instantize the variable
			// 2a. instantize server socket
			serverSocket = new ServerSocket(8001);
			// instantize shared object for all client, pass 3 constants as parameters
			pool = new SharedSocketPool(MAX_NO_OF_CONNECTION, ITEMCODE_FILE_PATH, TRANS_HIST_FILE_PATH);
			
			/* You can ignore this part. This is to flush the File output stream and close the stream when this program close */
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try{
						pool.cleanup();
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			});				
			
			//3. receive the client connection
			while(true){
				//4. Get the Client Socket from server socket by accepting client connection
				Socket socket = serverSocket.accept();
				//5. Create the ClientHandler object, pass the Shared Object and Client Socket to it
				ClientHandler cHandler = new ClientHandler(pool, socket);
				//6. Create and start the thread 
                Thread s_thread = new Thread(cHandler);
                s_thread.start();
			}
		}catch(Exception e){
			e.printStackTrace();
		}//end of try-catch block
	}//end of main thread
	
}//end of class POSServer

class SharedSocketPool{
	private int maxNoOfConnection;
	private String itemCodeFilePath;
	private ReentrantLock lock;
	private Condition hasEmptySlot;
	private ArrayList<ClientHandler> handlers;//Pool that store active client handler object
	private HashMap<Integer, Double> itemPrice;
	private FileWriter fw;
	
	public SharedSocketPool(int _maxNoOfConnection, String _itemCodeFilePath, String _transFilePath){
		this.maxNoOfConnection = _maxNoOfConnection;
		
		this.itemCodeFilePath = _itemCodeFilePath;
		
		//6. How to instantize the lock object?
		lock = new ReentrantLock();
		//7. How to instantize the condition object?
              hasEmptySlot = lock.newCondition();
		
		//You need to first assign null to the ArrayList created. Pre-set the size of the ArrayList<>
		this.handlers = new ArrayList<ClientHandler>();
		for(int i = 0;i < this.maxNoOfConnection; i++){
			this.handlers.add(null);
		}
		
		//instantize the hash map to store the item code, price mapping
		this.itemPrice = new HashMap<Integer, Double>();
		
		//call the method so that it will load the "itemCode.csv" file into the itemPrice HashMap
		this.loadItemData();
		
		//call the method so that it will create/append and open the "transaction.csv" file output stream
		this.prepareFileWriter(_transFilePath);
	}
	
	/* Client will call this method to get client id. PLEASE implement the thread safe coding. */
	public int getClientId(ClientHandler _caller){ /*8a. Should we use "synchronized" here? Beware, it may not needed! */ 
		int clientId = -1;
		try{
			//8b. Should we use lock & condition here? Beware, it may not needed!
                        lock.lock();
			/*
				Hints : 
					If lock & condition needed here, what should we check? 
					And what should we do if the checking is true? 
					Line 56-58 may give you some insight	
                        */	
                        
			/* Send the "Please wait message to client IF MAXIMUM NUMBER OF CONNECTIONS IS REACH! */
                        while(maxNoOfConnection >= 5){            
			/* 	Be careful where to put this line. Try to test the program to see if max. no of connection reach, 
				will the waiting client show this message? */
                            _caller.WriteToClient("Please wait");
                            hasEmptySlot.await();
                        }
                        
			/* IF THERE ARE LESS THAN 5 CONNECTIONS, look up the space that is free, and get that index as client id */
			handlers.set(handlers.indexOf(null), _caller);
			/* Assign the client (i.e. the caller) to that free slot */
			clientId = handlers.indexOf(_caller);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			//8c. Anything need to write here? (unlock)
                     hasEmptySlot.signalAll();
                     lock.unlock();
		}
		return clientId;
	}
	
	/* Client will call this method when they send the command "Q" to server, to release a slot in the ArrayList<ClientHandler> */
	public void releaseClient(ClientHandler _caller){ /*9a. Should we use "synchronized" here? Beware, it may not needed! */
		try{
			//9b. Should we use lock here? Beware, it may not needed! /lock
                     lock.lock();
			//To free the slot, just assign a null to that slot.
                     handlers.set(handlers.indexOf(_caller), null);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			//9c. Anything need to write here? //(unlock)
                     lock.unlock();
		}
	}
	
	synchronized public double getUnitPrice(int itemCode){ /*10a. Should we use "synchronized" here? Beware, it may not needed! */ //sync
		//9b. Should we use lock here? Beware, it may not needed!		
		/* The following code is shortcut of if-else, if the itemCode parameter found in the HashMap, then return the price, if no, return -1 */
		return this.itemPrice.containsKey(itemCode) ? this.itemPrice.get(itemCode) : -1;
	}
	
	/* This is a method that allow client to pass in the client id + total and write in the "transaction.csv" */
	synchronized public void writeTransaction(int clientId, double total){ /*11a. Should we use "synchronized" here? Beware, it may not needed! */ //sync
		try {
			//11b. Should we use lock here? Beware, it may not needed!
			this.fw.write(String.format("%d,%f\n", clientId, total)); //Write the content to the file
			this.fw.flush();//flush the stream so that it write to the file immediately
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//It will triggered before the main program close
	public void cleanup(){
		try {
			this.fw.flush();
			this.fw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//This is a method to read the file content and load into the Item Price HashMap
	private void loadItemData(){
		try{
			Scanner fIn = new Scanner(new File(this.itemCodeFilePath));
			while(fIn.hasNextLine()){
				String line = fIn.nextLine();
				
				//Split the data
				String[] values = line.split(",");/* 12a. Google => Java how to split the string with delimiter , */
				
				try{
					int itemCode = Integer.parseInt(values[0]);/* 12b. Google => java how to cast string to int */
					double price = Double.parseDouble(values[1]);/* 12c. Google => java how to cast string to double */
					itemPrice.putIfAbsent(itemCode, price);
				} catch(NumberFormatException nfe) {/* 12d. If the input is not a numeric value, what exception will throw? */
					System.out.println("Invalid data file data : " + line);
					continue;
				}
			}
			fIn.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	//This is a method to prepare and open the file output stream
	private void prepareFileWriter(String transFilePath){	
		try{
            this.fw = new FileWriter(transFilePath, true);/*13a. How to instantize a FileWriter, and append the file, not overwrite the file? */;
		}catch(IOException ioe){
			ioe.printStackTrace();
		}
	}
}

class ClientHandler implements Runnable/* 14. how to make this class multithreading? */ {
	
	private final String ERR_MSG = "Invalid Command";
	private SharedSocketPool sharedPoolObj;
	private Socket skt;
	private BufferedReader in;
	private PrintWriter out;
	private char lastCommandCode, thisCmdCode;
	private String cmd;
	private int clientId;
	private double myTotal;
	
	public ClientHandler(SharedSocketPool _sharedPoolObj, Socket _skt){
		try{
			this.sharedPoolObj = _sharedPoolObj;
			this.skt = _skt;
			this.in = new BufferedReader(new InputStreamReader(skt.getInputStream()));/* 15. Get the socket input stream from socket and create a reader object to wrap the inputstream */
			this.out = new PrintWriter(skt.getOutputStream(), true) ;/* 16. Get the socket output stream from socket and create a writer object to wrap the outputstream, beware, with auto flush */
			this.lastCommandCode = ' ';
			this.thisCmdCode = ' ';
			this.cmd = "";
			this.myTotal = 0;
		}catch(Exception e){
			System.out.println("Connection Closed");
			closeConn();
		}//end of try-catch block			
	}
	
	public void run()/* 17. What is this method? */{
		try{
			boolean isRunning = true;
			// boolean isStart = true;
                                    
			while(isRunning){


				WriteToClient("Hello");
				System.out.println("Wait for User Input: ");
						
				String[] values = null;
				
				cmd = in.readLine();/* 18. How to get client input from object "in"? */
				System.out.println(cmd);
				
				/*
					19. Implement the logic for 
						a. Validate user input syntax and sequence
						b. Here is a hint on how to implement validation. There are 2 char variables defined, think of their usage according to it's name
						c. Perform the logic of according to the questions. e.g. "Q" to close the connection and free the slot in shared object
						d. Send the response message to client. You may use this.WriteToClient("Hello"); to send a "Hello" to client
						e. Make use of shared object public method to complete the logic : getClientId(), releaseClient(), getUnitPrice(), writeTransaction()
						   Pass the correct parameters to these method
						f. You may use this.closeConn(); to close the socket connection between server and client.
						g. You may need to split the input string command, and then extract the parameter inside (e.g. R:123:7)
						h. You may also need to reference 12b, 12c to get the insight of how to extract int/double from client input
						i. If validation failed, or anything went wrong (e.g. get unit price < 0), then send error message to client by using this.WriteToClient()
						   and constant "ERR_MSG", and remember to use continue to skip remaining code, and back to get client input.
				*/
				
				
				//Validation - check empty command
                if(cmd == null){
                    WriteToClient(ERR_MSG);
                }

				//Branch the logic according to the command code
				thisCmdCode = cmd.charAt(0);/* How to get the command code (i.e. C, R, X, Q) ONLY? */ //java split()
				
				switch(thisCmdCode){
					
					case 'C':
						//Check previous command code
						if(lastCommandCode != 'C'){
						//Get client id from sharedObject
                            sharedPoolObj.getClientId(this);
						//Record this command code as last command code, so that you can validate against in next round of client input sequence validation
                            lastCommandCode = thisCmdCode;
						//Use the local private method to send the client id to client
                            WriteToClient(Integer.toString(clientId));
						break;
                    }
                                                
					case 'R':
						//Check previous command code
						if(lastCommandCode != 'R'){
						//Extract the parameter - split into 3 segments
                            values = cmd.split(":");
                        } else{ //If the command does not contains 3 parts, send error message to client                                                
                            WriteToClient(ERR_MSG);
                        } 
                                                
						int itemCode, qty;
						//Extract the parameter - get the item code and quantity
						//You may refer to 12b, 12c
						//Remember if the number format is not correct, also send error message to client
						try{
                            itemCode = Integer.parseInt(values[1]);
                            qty = Integer.parseInt(values[2]);                                                    
                        } catch(NumberFormatException nfe) {
                            System.out.println(ERR_MSG);
                            continue;
                        }                                                
                                                
						//Get the unit price from shared object HashMap
						//If item code not exists, what will be returned from Shared Object?
						//If item code not exists, send error message to client
						sharedPoolObj.getUnitPrice(itemCode);
                                                
                        if(itemCode == 0){                                                    
                             WriteToClient(ERR_MSG);
                        }
                                                
						//Calculate the sub total
						double subTotal = itemCode * qty;
                                                
						//aggregate the myTotal with sub-total
						myTotal += subTotal;
                                                
						//Record this command code as last command code, so that you can check in next round of client input sequence validation
						lastCommandCode = thisCmdCode;
                                                
						break;
                                                
                                                
					case 'X':
						//Check previous command code
						if(lastCommandCode != 'X'){
                            WriteToClient(ERR_MSG);
                        }
						//send the myTotal to client
						
						//By using one of the Shared Object public method, write the clientId, myTotal to the "transaction.csv"
                        sharedPoolObj.writeTransaction(clientId, myTotal);
						//Anything need to perform if the transaction end? Hints, related to a numeric variable
                        myTotal = 0; 
						//Record this command code as last command code, so that you can check in next round of client input sequence validation
                        lastCommandCode = thisCmdCode;
                                                
						break;
                                                
                                                
					case 'Q':
						//Check previous command code
						if(lastCommandCode != 'Q'){
						//Use the local private method to close the socket connection + release a free slot in shared object arraylist
                            sharedPoolObj.releaseClient(this);    
                            closeConn();
                                                
                            isRunning = false;//break the while loop
						
						//Record this command code as last command code, so that you can check in next round of client input sequence validation
						lastCommandCode = thisCmdCode;
						break;
                    }
                                                
					default:
						//send error message to client
                        System.out.println(ERR_MSG);
						//remember to use continue to skip remaining code and get client next command
                        continue;
				}//end of switch-case
				
			}
		}catch(Exception e){
			System.out.println("Connection Closed");
			//Use the local private method to close the socket connection + release a free slot in shared object arraylist
                closeConn();
		}//end of try-catch block
	}//end of run()
	
	public void WriteToClient(String msg){
		try{
			this.out.println(msg);
		}catch(Exception e){
			System.out.println("Connection Closed");
			//Use the local private method to close the socket connection + release a free slot in shared object arraylist
                     closeConn();
		}			
	}
	
	//This is a local private method to close the socket connection + release a free slot in shared object arraylist
	private void closeConn(){
		try{
			//20. use shared object public method to release a free slot in the ArrayList
                        sharedPoolObj.cleanup();
			//21. close all input, output stream and socket
			in.close();
			out.close();
			skt.close();
		}catch(Exception e){
			System.out.println("Connection Closed");
		}
	}
}