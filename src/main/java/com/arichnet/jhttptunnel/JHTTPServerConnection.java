package com.arichnet.jhttptunnel;

import java.io.*;
import java.net.*;
import java.util.*;

class JHTTPServerConnection
{
	private MySocket mySocket = null;
	private String forward_host = null;
	private int forward_port = 0;
	private String session_id = "";
	private final String rootDirectory = ".";
	private final String defaultFile = "index.html";	
	private ForwardClient forward_client = null;

	JHTTPServerConnection(Socket s, String h, int p, ForwardClient f) throws IOException	{
		super ();
		mySocket = new MySocket (s);
		forward_host = h;
		forward_port = p;
		forward_client = f;
	}

	
	public void newsocket(){
		String socket_readline = "";
		String http_method = "";
		String temp = "";
		
		Hashtable<String, String> http_headers = new Hashtable();
		Hashtable<String, String> http_arguments = new Hashtable<String, String>();
		
		System.out.println("Thread: " + Thread.currentThread().getName() + " | New TCP Connection started");
		
		socket_readline = mySocket.readLine();
		System.out.println("Thread: " + Thread.currentThread().getName() + 
				           " | First line data received: " + socket_readline);
		temp = socket_readline.substring(socket_readline.indexOf("/"));
		temp = temp.substring(0, temp.indexOf(" "));
		if (temp.length() > 1)
			http_arguments = getURLarguments(temp);
		
		if ((temp = http_arguments.get("SESSIONID")) != null)
			session_id = temp;
		else
			session_id = "123456789";				
		
		// Get the session ID to identify the client Thread
		
		// The thread name will depend on the Session ID
		// Thread.currentThread().setName("1234456");
		
		http_method = socket_readline.substring(0, socket_readline.indexOf(" "));
		System.out.println("Thread: " + Thread.currentThread().getName() + 
		           		   " | Method of this socket: " + http_method);
		
		http_headers = getHttpHeader(mySocket);
		
		switch(http_method.toLowerCase()) {
			case "post":
				// Start the client with the Session ID specified
				forward_client.setForwardClientData(forward_host, forward_port, session_id);
				Thread client_thread = new Thread(forward_client, session_id);
				client_thread.start();
				
				System.out.println("Thread: " + Thread.currentThread().getName() + 
								   " | POST called: " + forward_client.toString());
				
				processPOST(mySocket, http_headers, http_arguments, forward_client);				
				break;
			case "get":
				System.out.println("Thread: " + Thread.currentThread().getName() + 
						           " | GET called: " + forward_client.toString());
				processGET(mySocket, http_headers, http_arguments, forward_client);
				break;
			case "head":
				System.out.println("HEAD called");
				break;
			default:
				System.out.println("NOT ALLOWED");
				break;
		}
	}	
	
	private Hashtable<String, String> getHttpHeader(MySocket socket) {
		Hashtable<String, String> return_data = new Hashtable(); 
		//Vector return_data = new Vector();
		String temp = null;
		String key = "";
		String value = "";
		
		while (true){
			temp = socket.readLine();			
			if (temp.length() == 0) {
				break;
			}
			key = temp.substring(0,temp.indexOf(":"));
			value = temp.substring(temp.indexOf(":") + 1).trim();
			return_data.put(key, value);
			System.out.println("Thread: " + Thread.currentThread().getName() + 
					           " | HEADERS (Key:Value) " + key + ":" + value);
		}
		return return_data;
	}
	
	private Hashtable<String, String> getURLarguments(String URL){		
		String key;
		String value;
		// (ForwardClient) forward_client.currentThread() 
		Hashtable<String, String> return_value = new Hashtable<String, String>();
		try {
			if (URL.indexOf("?") != -1) {
				URL = URL.substring(URL.indexOf("?"));
				do {
					URL = URL.substring(1);
					key = URLDecoder.decode(URL.substring(0, URL.indexOf("=")), "UTF-8");
					if (URL.indexOf("&") > 0) {
						value = URLDecoder.decode(URL.substring(URL.indexOf("=") + 1, URL.indexOf("&")),"UTF-8");
						URL = URL.substring(URL.indexOf("&"));						
					}
					else {
						value = URLDecoder.decode(URL.substring(URL.indexOf("=") + 1),"UTF-8");
						URL = "";
					}
					return_value.put(key, value);
					System.out.println("Thread: " + Thread.currentThread().getName() + 
							           " | ARGUMENTS (Key:Value) " + key + ":" + value);
				} while(URL.indexOf("&") != -1);
			}
		}
		catch (Exception e){
			return_value = null;
			System.out.println("Exception occured: " + e.toString());
		}
		return return_value;
	}
	
	private void processPOST(MySocket socket, 
							 Hashtable<String, String> http_headers, 
							 Hashtable<String, String> http_arguments,
							 ForwardClient forward_client){
		byte[] buff = new byte[1024];
		try {
			System.out.println("Thread: " + Thread.currentThread().getName() + 
					           " | Starting POST processing: " + forward_client.toString());
			byte controlbyte = 0;
			int temp = 0;
			boolean tunnel_opened = false;			
			int data_length = 0;
			
			// Get first byte to start the tunnel
			//temp = socket.read(buff, 0, 1);
			//controlbyte = buff[0];
			//temp = socket.read(buff, 0, 3);
									
			while (true) {

				if (tunnel_opened) {

					System.out.println("Thread: " + Thread.currentThread().getName() + 
							 		   " | Inside Checking: " + socket.available());
					socket.getStatus();
					
					if (socket.available() > 0) {						
						temp = socket.read(buff, 0, 1);
						controlbyte = buff[0];
						System.out.println("Thread: " + Thread.currentThread().getName() + 
										   " | First byte received: " + Integer.toHexString(controlbyte));						
																		
						if (controlbyte == JHttpTunnel.TUNNEL_DATA) {
							temp = socket.read(buff, 0, 2);
							data_length = (Byte.valueOf(buff[0]) * 255) + Byte.valueOf(buff[1]);
							System.out.println("Thread: " + Thread.currentThread().getName() + 
									   		   " | POST Data Length: " + data_length);
																				
							temp = socket.read(buff, 0, data_length);
							forward_client.writeOutputBuffer(Arrays.copyOfRange(buff, 0, data_length));

						}
						else if (controlbyte  == JHttpTunnel.TUNNEL_PAD1) {
							System.out.println("Thread: " + Thread.currentThread().getName() + 
							   		   " | Comparing with: " + Integer.toHexString(JHttpTunnel.TUNNEL_PAD1));
							forward_client.sendPAD1();
						}
						
					}					
				}
				else {
					temp = socket.read(buff, 0, 1);
					controlbyte = buff[0];
					System.out.println("Thread: " + Thread.currentThread().getName() + 
							   		   " | First byte received: " + Integer.toHexString(controlbyte));					
					if ((controlbyte & 0xFF) == JHttpTunnel.TUNNEL_OPEN) {
						temp = socket.read(buff, 0, 3);
						tunnel_opened = true;
						System.out.println("Thread: " + Thread.currentThread().getName() + 
						           		   " | Tunnel Opened");
					}
					else {
						System.out.println("Thread: " + Thread.currentThread().getName() + 
										   " | Tunnel not yet opened");
					}
				}
				
				System.out.println("Thread: " + Thread.currentThread().getName() + 
						   		   " | Waiting for data to receive");
				Thread.currentThread().sleep((long)1000);									
					
			}
			
//			System.out.println("Exiting: ");
						
		}
		catch (Exception e) { 
			StringWriter errors = new StringWriter();
	        e.printStackTrace(new PrintWriter(errors));
			System.out.println("Thread: " + Thread.currentThread().getName() + 
			   		   		   " | POST Error: " + errors.toString());
		}
	}
	
	private void processGET(MySocket socket, 
			 				Hashtable<String, String> http_headers, 
			 				Hashtable<String, String> http_arguments,
			 				ForwardClient forward_client){
		byte[] buff = new byte[1024];
		byte[] data_length = new byte[2];
		try{
			System.out.println("Thread: " + Thread.currentThread().getName() + 
					           " | Starting GET processing: " + forward_client.toString());
			
			sendok(socket);
			
			while(true){
				System.out.println("Thread: " + Thread.currentThread().getName() + 
				   		   		   " | GET Waiting for data buffer pos: " + forward_client.getBufferInPosition());
				
				if (forward_client.getBufferInPosition() > 0) {
					int position = forward_client.getBufferInPosition();
					byte[] tmp = forward_client.readInputBuffer();
					
					if ((position == 1) & (tmp[0] == JHttpTunnel.TUNNEL_PAD1)) {
						socket.write(tmp, 0, 1);
					}
					else {
						System.out.println("Thread: " + Thread.currentThread().getName() + 
						           		   " | WRITING GET DATA: " + tmp.length);
						buff[0] = JHttpTunnel.TUNNEL_DATA;
						socket.write(buff, 0, 1);					
						data_length = getDataLength(tmp.length);
						socket.write(data_length, 0, 2);					
						socket.write(tmp, 0, tmp.length);
					}					
				}
				
				Thread.currentThread().sleep((long)1000);
			}
			
		}
		catch (Exception e){
			StringWriter errors = new StringWriter();
	        e.printStackTrace(new PrintWriter(errors));
			System.out.println("Thread: " + Thread.currentThread().getName() + 
	   		   		   		   " | GET Error: " + errors.toString());
		}
	}
	
	private void sendok(MySocket socket) throws IOException {
		System.out.println("Thread: " + Thread.currentThread().getName() + " | Sending GET OK");
		socket.println("HTTP/1.1 200 OK");
		socket.println("Content-Length: " + JHttpTunnel.CONTENT_LENGTH);
		socket.println("Connection: close");
		socket.println("Pragma: no-cache");
		socket.println("Cache-Control: no-cache, no-store, must-revalidate");
		socket.println("Expires: 0");
		socket.println("Content-Type: text/html");
		socket.println("");
	}
	
	private byte[] getDataLength(int length) {
		byte[] return_data = new byte[2];		
		int msb_int = Math.abs(length / 256);
		int lsb_int = (length - (msb_int * 256));
		System.out.println("Thread: " + Thread.currentThread().getName() + 
   		   		   		   " | MSB value:" + msb_int + "- LSB value:" + lsb_int);		
		return_data[0] = (byte)msb_int;
		return_data[1] = (byte)lsb_int;
		
		return return_data;
	}
}