package com.arichnet.jhttptunnel;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.log4j.*;

class JHttpServerConnection {
	// Initialise Logger
	private static final Logger log = Logger.getLogger(JHttpServerConnection.class);
	private MySocket mySocket = null;
	private String forward_host = null;
	private int forward_port = 0;
	private String session_id = "";
	private ForwardClient forward_client = null;
	private OutBoundServer out_server = null;
	private InBoundServer in_server = null;
	private Hashtable<String, ForwardClient> clientsTable = null;
	private Hashtable<String, BoundServer> outBoundServerTable = null;
	private Hashtable<String, BoundServer> inBoundServerTable = null;
	private List<Integer> postRemotePorts = null;
	private boolean tunnel_already_opened = false;	

	JHttpServerConnection(Socket s, String h, int p, ForwardClient f) throws IOException {
		super();
		mySocket = new MySocket(s);
		forward_host = h;
		forward_port = p;
		forward_client = f;
	}

	JHttpServerConnection(Socket s, String h, int p, Hashtable T) throws IOException {
		super();
		mySocket = new MySocket(s);
		forward_host = h;
		forward_port = p;
		clientsTable = T;
	}

	JHttpServerConnection(Socket s, String h, int p, Hashtable T, List<Integer> l) throws IOException {
		super();
		mySocket = new MySocket(s);
		forward_host = h;
		forward_port = p;
		clientsTable = T;
		postRemotePorts = l;
	}
	
	JHttpServerConnection(Socket s, String h, int p, Hashtable T, 
						  List l, Hashtable o, Hashtable in) throws IOException {
		super();
		mySocket = new MySocket(s);
		forward_host = h;
		forward_port = p;
		clientsTable = T;
		postRemotePorts = l;
		outBoundServerTable = o;
		inBoundServerTable = in;
	}

	public void newsocket() {
		String socket_readline = "";
		String http_method = "";
		String temp = "";

		Hashtable<String, String> http_headers = new Hashtable<String, String>();
		Hashtable<String, String> http_arguments = new Hashtable<String, String>();
		
		log.info("New TCP Connection started");


		socket_readline = mySocket.readLine();
		if (socket_readline == null) {
			try {
				log.debug("Socket Error: " + socket_readline + " | " + 
						  "Socket Data Available:" + mySocket.available());
			}
			catch (IOException e) {				
			}
			do {
			   mySocket.close();
			} while (!mySocket.isClosed());
			
			return;
		}		
		
		temp = socket_readline.substring(socket_readline.indexOf("/"));
		temp = temp.substring(0, temp.indexOf(" "));
		if (temp.length() > 1)
			http_arguments = getURLarguments(temp);

		// Get the session ID to identify the client Thread from GET parameters
		if ((temp = http_arguments.get("SESSIONID")) != null)
			session_id = temp;
		else
			session_id = "123456789";		

		http_method = socket_readline.substring(0, socket_readline.indexOf(" "));		
		log.info("Method of this socket: " + http_method);

		http_headers = getHttpHeader(mySocket);

		switch (http_method.toLowerCase()) {
		case "post":
			// Create the OutBoundServer Table to reflect this session id
			if (outBoundServerTable.containsKey(session_id)) {
				log.debug("Recovering server outbound buffers with sid: " + session_id);
				out_server = (OutBoundServer) outBoundServerTable.get(session_id); 
			}
			else {				
				out_server = new OutBoundServer();
				outBoundServerTable.put(session_id, out_server);
				log.debug("Add server outbound buffer with sid : " + session_id);	
			}
			
			Integer remote_port = new Integer(mySocket.getRemotePort());
			postRemotePorts.add(remote_port);
			log.debug("Remote Ports List: " + Arrays.toString(postRemotePorts.toArray()));			
						
			// Get the session id client if it has been initiated
			if (clientsTable.containsKey(session_id)) {
				log.debug("Recovering client with sid: " + session_id);
				forward_client = (ForwardClient) clientsTable.get(session_id);
				forward_client.message();
				tunnel_already_opened = true;
			} else {
				forward_client = new ForwardClient();
				forward_client.setForwardClientData(forward_host, forward_port, session_id, out_server);
				Thread client_thread = new Thread(forward_client, session_id);
				client_thread.start();
				// Saving forward_client on the hash
				clientsTable.put(session_id, forward_client);
				log.debug("Adding client to the table " + clientsTable.containsKey(session_id));
				tunnel_already_opened = false;
				// forward_client.message();
			}
			
			out_server.setForwardClient(forward_client);
			
			// Start the client with the Session ID specified
			log.info("POST called: " + forward_client.toString());			

			processPOST(mySocket, http_headers, http_arguments, tunnel_already_opened, remote_port, out_server);
			
			postRemotePorts.remove(remote_port);
			
			break;

		case "get":
			
			while (!clientsTable.containsKey(session_id)) {
				// Waiting for the forward client to start
				try {
					Thread.currentThread().sleep((long) 10);
				} catch (Exception e) {
				}
			}
			forward_client = (ForwardClient) clientsTable.get(session_id);
			log.info("GET called: " + forward_client.toString());			

			// Create the OutBoundServer Table to reflect this session id
			if (inBoundServerTable.containsKey(session_id)) {
				log.debug("Recovering server inbound buffers with sid: " + session_id);
				in_server = (InBoundServer) inBoundServerTable.get(session_id); 
			}
			else {
				in_server = new InBoundServer();
				inBoundServerTable.put(session_id, in_server);
				log.debug("Add server inbound buffer with sid : " + session_id);				
			}
			
			in_server.setForwardClient(forward_client);
			forward_client.setInboundServer(in_server);
						
			// Wait until GET is unlocked
			while (in_server.fcl_getGETLocked()) {
				try {
					Thread.currentThread().sleep((long) 10);
				} catch (Exception e) {
				}
			}
			
			processGET(mySocket, http_headers, http_arguments, in_server);
						
			break;

		case "head":
			log.info("HEAD called");
			break;
		default:
			log.error("Method NOT allowed");
			break;
		}
	}

	private Hashtable<String, String> getHttpHeader(MySocket socket) {
		Hashtable<String, String> return_data = new Hashtable();
		
		String temp = null;
		String key = "";
		String value = "";

		while (true) {
			temp = socket.readLine();
			if (temp.length() == 0) {
				break;
			}
			key = temp.substring(0, temp.indexOf(":"));
			value = temp.substring(temp.indexOf(":") + 1).trim();
			return_data.put(key, value);
			log.debug("HEADERS (Key:Value) " + key + ":" + value);
		}
		return return_data;
	}

	private Hashtable<String, String> getURLarguments(String URL) {
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
						value = URLDecoder.decode(URL.substring(URL.indexOf("=") + 1, URL.indexOf("&")), "UTF-8");
						URL = URL.substring(URL.indexOf("&"));
					} else {
						value = URLDecoder.decode(URL.substring(URL.indexOf("=") + 1), "UTF-8");
						URL = "";
					}
					return_value.put(key, value);
					log.debug("ARGUMENTS (Key:Value) " + key + ":" + value);
					
				} while (URL.indexOf("&") != -1);
			}
		} catch (Exception e) {
			return_value = null;
			log.error("Exception occured: " + e.toString());
		}
		return return_value;
	}

	private void processPOST(MySocket socket, Hashtable<String, String> http_headers,
			Hashtable<String, String> http_arguments, boolean tunnel_already_opened,
			int remote_port, OutBoundServer out_server) {
		
		byte[] buff = new byte[JHttpTunnel.BUFFER_LENGTH];
		try {
			log.info("Starting POST processing: " + out_server.toString());
			byte controlbyte = 0;
			int temp = 0;
			boolean tunnel_opened = tunnel_already_opened;
			int data_length = 0;
			boolean keep_request = true;
			int postTraffic = 0;
			
			// Initialise the buffer for this port
			out_server.initPort(remote_port);

			do {
				if (out_server.getTunnelOpened()) {										
					if (socket.available() > 0) {
						// Get the first control byte						
						temp = socket.read(buff, 0, 1);						
						postTraffic++;
						controlbyte = buff[0];
						log.debug("Control Byte Received: " + controlbyte);

						switch (controlbyte) {
						case JHttpTunnel.TUNNEL_DATA:
							temp = socket.read(buff, 0, 2);
							postTraffic += 2;
							data_length = ((buff[0] & 0xFF) << 8) + (buff[1] & 0xFF);
							// Check if the size is higher than the limit
							// Stop get or post after processing this data
							if ((data_length + postTraffic) > JHttpTunnel.CONTENT_LENGTH) {
								keep_request = false;
								log.debug("Traffic Limit Reached: " + (data_length + postTraffic) + 
										  " | Continue Tunnel Flag: " + keep_request);
							}

							log.debug("POST Data Length: " + data_length + 
									  " | Continue Tunnel Flag: " + keep_request);

							do {
								if (out_server.getBufferPosition(remote_port) <= 0) {
									if (data_length > JHttpTunnel.BUFFER_LENGTH){
										socket.read(buff, 0, JHttpTunnel.BUFFER_LENGTH);
										postTraffic += JHttpTunnel.BUFFER_LENGTH;
										data_length -= JHttpTunnel.BUFFER_LENGTH;
										out_server.writeData(buff, remote_port);			
									}
									else {
										socket.read(buff, 0, data_length);
										postTraffic += data_length;
										out_server.writeData(Arrays.copyOfRange(buff, 0, data_length),remote_port);
										data_length = 0;
									}
									
								}

							} while (data_length > 0);
							// Print the status of the buffers
							log.debug("* Buff Status: " + out_server);							
							break;

						case JHttpTunnel.TUNNEL_PAD1:
							log.info("Server PAD received");							
							break;

						case JHttpTunnel.TUNNEL_DISCONNECT:
							keep_request = false;
							log.info("Disconnecting the tunnel!! ");														
							break;

						case JHttpTunnel.TUNNEL_CLOSE:
							keep_request = false;
							log.info("Closing the tunnel!!");
							//forward_client.setCONTROL(JHttpTunnel.TUNNEL_CLOSE);
							//tunnel_opened = false;
							out_server.setTunnelOpened(false);
							//closeForwardClient(out_server);
							closeForwardClient();
							break;
						}
					}

				} else {
					temp = socket.read(buff, 0, 1);					
					postTraffic++;
					controlbyte = buff[0];
					log.info("Tunnel not yet Opened, Control Byte Received: " + controlbyte);					

					if ((controlbyte & 0xFF) == JHttpTunnel.TUNNEL_OPEN) {
						temp = socket.read(buff, 0, 3);
						postTraffic += 3;
						out_server.setTunnelOpened(true);
						//tunnel_opened = true;
						log.info("Openning http tunnel");
						
					}
				}

				Thread.currentThread().sleep((long) 10);
			} while (keep_request && (!forward_client.isClosed()));

			log.info("About to CLOSE POST socket... ");
			out_server.removePort(remote_port);
			close(socket);

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("POST processing Errors: " + errors.toString());
		}
	}

	private void processGET(MySocket socket, Hashtable<String, String> http_headers,
			Hashtable<String, String> http_arguments, InBoundServer in_server) {
				
		byte[] buff = new byte[10240];
		byte[] data_length = new byte[2];
		int getTraffic = 0;
		boolean keep_request = true;
		int position = 0;
		int correction = 0;
		ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(4);

		try {
			in_server.fcl_setGETLocked(true);
			log.info("Starting GET processing: " + forward_client.toString());

			sendok(socket);
			getTraffic = correction;
			
			// Start the PAD sent only if the tunnel is opened
			while (!in_server.getTunnelOpened()) {
				Thread.currentThread().sleep((long) 10);
			}
			
			//We initialise the PAD send
			final MySocket fsocket = socket;
			final BoundServer fin_server = in_server;
			Runnable runnableSendPad = new Runnable() {
				byte[] pad = new byte[1];				
				@Override
				public void run() {
					pad[0] = JHttpTunnel.TUNNEL_PAD1;
					try {
					    fsocket.write(pad, 0, 1);
					    log.debug("Server PAD sent");
					    fin_server.fcl_message();
					} catch (Exception e){
						log.error("Error sending PAD");
					}
				}
			};
			scheduledPool.scheduleAtFixedRate(runnableSendPad, 5, 5, TimeUnit.SECONDS);

			do {
				if ((!in_server.isLocked()) && (in_server.hasData())) {
					in_server.lockTable();
					if ((in_server.nextBufferData() + getTraffic + 3) > (JHttpTunnel.CONTENT_LENGTH - 3)) {
						position = JHttpTunnel.CONTENT_LENGTH - getTraffic - 3;
						keep_request = false;
					}
					else {
						position = in_server.nextBufferData();
					}
					
					byte[] tmp = in_server.readTable(position);
					buff[0] = JHttpTunnel.TUNNEL_DATA;
					socket.write(buff, 0, 1);
					getTraffic++;
					data_length = getDataLength(tmp.length);
					socket.write(data_length, 0, 2);
					getTraffic += 2;
					socket.write(tmp, 0, tmp.length);
					getTraffic += tmp.length;
					
					log.debug("GET Data Traffic: " + getTraffic + " | Length: " + 
					          tmp.length + " | Continue Tunnel Flag: " + keep_request);
					
					in_server.unlockTable();
				}								

				if (!keep_request) {
					buff[0] = JHttpTunnel.TUNNEL_DISCONNECT;
					socket.write(buff, 0, 1);
					getTraffic++;
				}

				if (in_server.getSendClose()) {
					log.debug("Closing the socket and sending CLOSE signal");
					buff[0] = JHttpTunnel.TUNNEL_CLOSE;
					socket.write(buff, 0, 1);
					keep_request = false;
					getTraffic++;
				}

				// Do nothing
				Thread.currentThread().sleep((long) 10);

			} while (keep_request && (!in_server.fcl_isClosed()));
			
			// Stopping scheduler
			scheduledPool.shutdown();
			log.info("About to CLOSE GET socket... ");
			close(socket);
			in_server.fcl_setGETLocked(false);				
			
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("GET Processing Errors: " + errors.toString());
		}
	}

	private void close(MySocket socket) {
		// Closing the thread
		while (!socket.isClosed()) {
			socket.close();
		}
	}

	private void closeForwardClient() {		
		log.debug("Closing forward client for session id: " + session_id);
		// // Close the Forward Client
		BoundServer bserver = (BoundServer) outBoundServerTable.get(session_id);
		BoundServer bserver2 = (BoundServer) inBoundServerTable.get(session_id);

		while (!bserver.fcl_isClosed()) {
			bserver.fcl_close();
		}

		//null the BoundServers
		bserver = null;
		bserver2 = null;
		
		// Remove the client from all hash tables
		outBoundServerTable.remove(session_id);
		inBoundServerTable.remove(session_id);
		clientsTable.remove(session_id);				
	}

	private void sendok(MySocket socket) throws IOException {
		log.debug("Sending GET 200 OK");
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
		return_data[0] = (byte) msb_int;
		return_data[1] = (byte) lsb_int;
		return return_data;
	}
	
}

