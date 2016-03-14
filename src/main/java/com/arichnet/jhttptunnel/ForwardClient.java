package com.arichnet.jhttptunnel;

import java.net.*;
import java.io.*;

public class ForwardClient implements Runnable{
	String forward_host = "127.0.0.1";
	String session_id = "";
	int forward_port = 0;
	Socket forward_socket = null;
	InputStream forward_in = null;
	OutputStream forward_out = null;
	ByteArrayInputStream buffer_in = null;
	ByteArrayOutputStream buffer_out = null;
	
	public ForwardClient(String fhost, int fport, String sid){
		forward_host = fhost;
		forward_port = fport;
		session_id = sid;
	}

	@Override
	public void run() {
		try {
			// Connect to forward server
			byte[] data = null;
			forward_socket = new Socket(forward_host, forward_port);
			forward_in = forward_socket.getInputStream();
			forward_out = forward_socket.getOutputStream();
			while (forward_socket.isConnected()) {
				if (forward_in.available() > 0) {
					System.out.println("Thread: " + Thread.currentThread().getName() + 
			                   		   " | DATA IN: " + forward_in.available());
					forward_in.read(data, 0, forward_in.available());
					buffer_in = new ByteArrayInputStream(data); 
				}
				if (buffer_out.size() > 0) {
					System.out.println("Thread: " + Thread.currentThread().getName() + 
	                   		   		   " | DATA IN: " + buffer_out.size());
					data = buffer_out.toByteArray();
					forward_out.write(data, 0, data.length);
				}
				Thread.currentThread().sleep((long)10);
			}
		}		
		catch (Exception e) {
			System.out.println("Thread: " + Thread.currentThread().getName() + 
			                   " | ForwardClient error: " + e.toString());
		}
		
	}
	
	public byte[] readInputStream(){
		byte[] return_data = null;
		System.out.println("Thread: " + Thread.currentThread().getName() + 
     		   			   " | Byte Input Stream Size: " + buffer_in.available());
		buffer_in.read(return_data, 0, buffer_in.available());
		return return_data;
	}
	
	public void writeOutputStream(byte[] bytes_data){
		buffer_out.write(bytes_data, buffer_out.size(), bytes_data.length);
		System.out.println("Thread: " + Thread.currentThread().getName() + 
                		   " | Byte Output Stream Size: " + buffer_out.size());
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

