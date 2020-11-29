/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
//package posclient;
/**
 *
 * @author mfng
 */
import java.io.*;
import java.net.*;

public class POSClient{
	
	//1. Declare variables
	private static Socket skt;
	private static BufferedReader br, in;
	private static PrintWriter out;
	
	public static void main(String args[]){
		String cmd = "";
		
		try{
			/* You can ignore this part. This is to flush the File output stream and close the stream when this program close */
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try{
						out.close();
						in.close();
						br.close();
						skt.close();
					}catch(Exception e){
						System.out.println("Connection Closed");
					}
				}
			});			

			//2. Instantize variables declared in 1
			skt = new Socket("127.0.0.1", 8001);
			br = new BufferedReader(new InputStreamReader(System.in));
			in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
			out = new PrintWriter(skt.getOutputStream(), true);
             
			//3. How to instantize a thread class and run it?
            Thread thread = new Thread(new InputHandler(skt,out));
                        
            thread.start();
	
			/* 4. How to continuously get user input? Assign the input to variable "cmd" */			
            while((cmd = in.readLine()) != null){
				System.out.println(cmd);             
			}//end of loop

			//6. Close all the connection and socket
			out.close();
			in.close();
			br.close();
			skt.close();
		}catch(Exception e){
			System.out.println("Connection Closed");
		}//end of try-catch block
	}//end of main program

	public static void WriteToServer(String msg){
		try{
			System.out.println("Sent '"+msg+"' to server.");
			out.println(msg);
		}catch(Exception e){
			System.out.println("Connection Closed");
			//Use the local private method to close the socket connection + release a free slot in shared object arraylist
			closeConn();
		}			
	}

	private static void closeConn(){
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

//3. Create a multithreading class that handle server side input
class InputHandler implements Runnable/* 3a. how to make this class multithreading? */{
	
	//3b. Declare a variable that can read line from server
       Socket _ssk = null;
	   BufferedReader _br = null;
	   String sInput = "";    
	   PrintWriter _out = null;  
	
	public InputHandler(Socket ssk, PrintWriter out){/* 3c. What Kind of variable it should be pass from main program? */
		//3d. Assign the input parameter to the variable in 3b
			this._ssk = ssk; 
			_out = out;      
	}
	
	public void run()/* 3e. What is this method? */{
		try{
			/* 3f. How to continuously get the message returned from server? */    
				_br = new BufferedReader(new InputStreamReader(System.in));
                while((sInput = _br.readLine()) != null){
					//This one is a gift for you, when press "Q", break the loop of Questions-4
					if(sInput.equals("Q")){
						System.out.println("Quit the program.");
						break;
					}else{
						//5. How to send the variable "cmd" to server?
						System.out.println("Send to server: "+sInput); /* 3g. How to read line from server? */
						_out.println(sInput);
					}					
			}//end of loop
		}catch(Exception e){
			System.out.println("Connection Closed");
		}//end of try-catch block
	}
	
}