package com.arichnet.jhttptunnel;

import java.net.*;
import java.io.*;
import java.nio.*;

public class ForwardClient implements Runnable{
	String forward_host = "127.0.0.1";
	String session_id = "";
	int forward_port = 0;
	Socket forward_socket = null;
	InputStream forward_in = null;
	OutputStream forward_out = null;
	ByteBuffer buffer_in = ByteBuffer.allocateDirect(1024);
	ByteBuffer buffer_out = ByteBuffer.allocateDirect(1024);
	byte[] _rn = "\r\n".getBytes ();
	//ByteArrayInputStream buffer_in = new ByteArrayInputStream(new byte[1024]);
	//ByteArrayOutputStream buffer_out = new ByteArrayOutputStream();
	
	public ForwardClient(String fhost, int fport, String sid){
		forward_host = fhost;
		forward_port = fport;
		session_id = sid;
	}
	
	public ForwardClient(){
		forward_host = "127.0.0.1";
		forward_port = 22;
	}
	
	public void setForwardClientData(String h, int p, String s){
		forward_host = h;
		forward_port = p;
		session_id = s;
	}

	@Override
	public void run() {
		try {
			// Connect to forward server
			byte[] data_to_send = new byte[1024];
			byte[] data_to_receive = new byte[1024];
			int data_size = 0;
			
			forward_socket = new Socket(forward_host, forward_port);
			forward_socket.setTcpNoDelay(true);
			
			forward_in = forward_socket.getInputStream();
			forward_out = forward_socket.getOutputStream();
			
			while (forward_socket.isConnected()) {
				if (forward_in.available() > 0) {
					data_size = forward_in.available();
					forward_in.read(data_to_receive, 0, data_size);
					buffer_in.put(data_to_receive, 0, data_size);
					buffer_out.put(_rn, 0, 2);
					System.out.println("Thread: " + Thread.currentThread().getName() + 
	                   		   		   " | ++++ BUFFER IN POSITION: " + buffer_in.position());
				}
				if (buffer_out.position() > 0) {
					data_size = buffer_out.position();
					buffer_out.position(0);					
					buffer_out.get(data_to_send,0, data_size);
					
					System.out.println("Thread: " + Thread.currentThread().getName() + 
 		   		   		   			   " | **** SENDING DATA SIZE: " + data_size);
					forward_out.write(data_to_send, 0, data_size);					
					buffer_out.rewind();
					System.out.println("Thread: " + Thread.currentThread().getName() + 
            		   		   		   " | **** BUFFER OUT POSITION: " + buffer_out.position());
				}
				Thread.currentThread().sleep((long)1000);
			}
		}		
		catch (Exception e) {
			StringWriter errors = new StringWriter();
	        e.printStackTrace(new PrintWriter(errors));
			System.out.println("Thread: " + Thread.currentThread().getName() + 
			                   " | ForwardClient error: " + errors.toString());
		}
		
	}
	
	public byte[] readInputBuffer(){
		byte[] return_data = null;
		int currentPosition = buffer_in.position();
		if (currentPosition > 0) {
			return_data = new byte[currentPosition];
			buffer_in.rewind();
			buffer_in.get(return_data, 0, currentPosition);
			buffer_in.rewind();
		}		
		System.out.println("Thread: " + Thread.currentThread().getName() + 
     		   			   " | Byte Input Stream Size: " + currentPosition);		
		return return_data;
	}
	
	public int getBufferInPosition() {
		return buffer_in.position();
	}
	
	public void writeOutputBuffer(byte[] bytes_data){
		buffer_out.put(bytes_data, 0, bytes_data.length - 1);
		buffer_out.put(_rn, 0, 2);
		System.out.println("Thread: " + Thread.currentThread().getName() + 
                		   " | Byte Output Stream Size: " + bytes_data.length);
	}
	
	public void sendPAD1(){
		buffer_in.put(JHttpTunnel.TUNNEL_PAD1);
	}
	
	public void close(){
		try {
			while (!forward_socket.isConnected()) {
				forward_socket.close();
			}
		}
		catch (Exception e){ }
	}
}

