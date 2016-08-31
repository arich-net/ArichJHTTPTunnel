package com.arichnet.jhttptunnel;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.text.*;
import java.util.*;

import org.apache.log4j.Logger;

public class ForwardClient implements Runnable {
	private static final Logger log = Logger.getLogger(ForwardClient.class);
	String forward_host = "127.0.0.1";
	String session_id = "";
	int forward_port = 0;
	Socket forward_socket = null;
	InputStream forward_in = null;
	OutputStream forward_out = null;

	byte[] _rn = "\r\n".getBytes();
	byte[] control = new byte[1];
	boolean GETlocked = false;
	boolean POSTlocked = false;

	boolean tunnel_opened = false;
	//DateFormat date_format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
	private OutBoundServer out_server = null;
	private InBoundServer in_server = null;
	
	// ByteArrayInputStream buffer_in = new ByteArrayInputStream(new
	// byte[1024]);
	// ByteArrayOutputStream buffer_out = new ByteArrayOutputStream();

	public ForwardClient(String fhost, int fport, String sid) {
		forward_host = fhost;
		forward_port = fport;
		session_id = sid;
		control[0] = 0;
	}
	
	public ForwardClient(String fhost, int fport, String sid, OutBoundServer out) {
		forward_host = fhost;
		forward_port = fport;
		session_id = sid;
		control[0] = 0;
		out_server = out;
	}

	public ForwardClient() {
		forward_host = "127.0.0.1";
		forward_port = 22;
		session_id = "123456";
		control[0] = 0;
	}

	public void setForwardClientData(String h, int p, String s, OutBoundServer out) {
		forward_host = h;
		forward_port = p;
		session_id = s;
		control[0] = 0;
		out_server = out;
	}

	@Override
	public void run() {
		try {
			log.info("Starting forward client: " + this.toString());

			// Connect to forward server
			byte[] data_to_send = null;
			byte[] data_to_receive = null;
			int data_size = 0;

			forward_socket = new Socket(forward_host, forward_port);
			forward_socket.setTcpNoDelay(true);
			forward_socket.setSoTimeout(2000);

			forward_in = forward_socket.getInputStream();
			forward_out = forward_socket.getOutputStream();

			message();

			while (forward_socket.isConnected() && !forward_socket.isClosed()) {
				if ((in_server != null) && (forward_in.available() > 0) &&
					(!in_server.isLocked())) {					
					in_server.lockTable();
					log.debug("Forward Client data available: " + forward_in.available());
					if (forward_in.available() >= 10240) {
						data_to_receive = new byte[10240];
						forward_in.read(data_to_receive, 0, 10240);
						in_server.writeTable(data_to_receive);						
					} else {
						data_to_receive = new byte[forward_in.available()];
						forward_in.read(data_to_receive);
						//log.debug("ForwardIN Data: " + Arrays.toString(data_to_receive));
						in_server.writeTable(data_to_receive);						
					}
					in_server.unlockTable();					
				}
				// Read Data from OutBoundServer Buffer				
				data_to_send = out_server.readData();
				
				if (data_to_send != null) {
					forward_out.write(data_to_send);
					log.debug("ForwardOUT Length Data: " + data_to_send.length);
				}

				if (in_server.getSendClose()) {
					close();
				}
				
				Thread.currentThread().sleep((long) 10);
			}
		} catch (IOException e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("ForwardClient IOException Error - Cleaning socket: " + errors.toString());
			close();			
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("ForwardClient error: " + errors.toString());
		}

	}

	public byte[] getCONTROL() {
		return control;
	}

	public void setCONTROL(byte c) {
		control[0] = c;
	}

	public boolean isClosed() {
		boolean return_value = false;
		if (forward_socket != null)
			return_value = forward_socket.isClosed();
		return return_value;
	}

	public void close() {
		try {
			log.debug("Closing ForwardClient: " + this);
			in_server.setSendClose(true);
			// Wait some time for the CLOSE command to be sent by JHttpServerConnection
			Thread.currentThread().sleep((long) 50);

			while (!forward_socket.isClosed()) {				
				forward_socket.close();
			}
			while (!Thread.currentThread().interrupted()) {
				Thread.currentThread().interrupt();
			}
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("ForwardClient error: " + errors.toString());
		}
	}

	public boolean getGETlocked() {		
		return GETlocked;
	}

	public boolean getPOSTlocked() {
		return POSTlocked;
	}

	public void setGETlocked(boolean flag) {
		GETlocked = flag;
	}

	public void setPOSTlocked(boolean flag) {
		POSTlocked = flag;
	}
	
	public void setTunnelOpened(boolean flag){
		tunnel_opened = flag;
	}
	
	public boolean getTunnelOpened(){
		return tunnel_opened;
	}
	
	public void setInboundServer(InBoundServer in) {
		in_server = in;
		in_server.setSendClose(false);
	}
	
	public InBoundServer getInboundServer(){
		return in_server;
	}

	public void message() {
		log.debug("ForwardClient MESSAGE request Socket INFO: "	+ forward_socket.toString());
		log.debug("ForwardClient status: isBound:" + forward_socket.isBound() + 
                          " isClosed:" + forward_socket.isClosed() + 
		          " isConnected:" + forward_socket.isConnected());
	}

	public String getThreadID() {
		return Thread.currentThread().getName();
	}

}
