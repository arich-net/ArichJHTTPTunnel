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
	
	JHttpServerConnection(Socket s, String h, int p, Hashtable T, 
						  Hashtable o, Hashtable in) throws IOException {
		super();
		mySocket = new MySocket(s);
		forward_host = h;
		forward_port = p;
		clientsTable = T;
		outBoundServerTable = o;
		inBoundServerTable = in;
	}

	public String newsocket() throws IOException {
		String socket_readline = "";
		String http_method = "";
		String temp = "";
		String th_name = "";
		String thread_result = "continue";

		Hashtable<String, String> http_headers = new Hashtable<String, String>();
		Hashtable<String, String> http_arguments = new Hashtable<String, String>();

		Thread client_thread = null;
		
		log.info("New TCP Connection started");
		
		//byte[] small = new byte[1];
		//int numberread = mySocket.read(small, 0, 1);
		//log.debug("First Data Read: " + Arrays.toString(small) + " AVAILABLE:" + mySocket.available() + " READ:" + numberread);
		//while(mySocket.available() <= 0) {
		//	try {
		//		Thread.currentThread().sleep((long) 2000);
		//		log.debug("Socket available: " + mySocket.available());
		//	} catch (Exception e) {
		//	}
		//}
		//byte[] tempo = new byte[4];
		//mySocket.read(tempo, 0, 4);
		//log.debug("First Data Received: " + Arrays.toString(tempo));


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
			
			return thread_result;
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

			// Start the client with the Session ID specified
			log.info("POST method called");	

			// Renaming the Thread to ease clening-up tasks
			th_name = Thread.currentThread().getName();
			if (th_name.contains(session_id))
				Thread.currentThread().setName(th_name.split(":")[0] + ":" + session_id + "-post");
			else
				Thread.currentThread().setName(th_name + ":" + session_id + "-post");
			
			//********************************************************************************************
			//                     Initiliazing all tables for this SessionID 
			//********************************************************************************************

			// Create the ForwardClient Table to for this session id
			if (clientsTable.containsKey(session_id)) {				
				forward_client = (ForwardClient) clientsTable.get(session_id);
				log.debug("Forward Client recovered with sid: " + session_id);
				//forward_client.message();

			} else {
				// We start the Thread of ForwardClient
				forward_client = new ForwardClient();
				forward_client.setForwardClientData(forward_host, forward_port, session_id, out_server);
				client_thread = new Thread(forward_client, session_id);
				//client_thread.start(); We start the thread when the tunnel gets openned

				// Saving forward_client on the hash
				clientsTable.put(session_id, forward_client);
				log.debug("Adding Fclient to the clients table " + clientsTable.toString());

				// forward_client.message();
			}
			
			// Create the OutBoundServer Table to for this session id
			if (outBoundServerTable.containsKey(session_id)) {				
				out_server = (OutBoundServer) outBoundServerTable.get(session_id); 
				log.debug("Recovered server outbound buffers with sid: " + session_id);
			}
			else {				
				out_server = new OutBoundServer();
				outBoundServerTable.put(session_id, out_server);
				log.debug("Add server outbound buffer with sid : " + session_id);	
			}

			Integer remote_port = new Integer(mySocket.getRemotePort());
			out_server.setForwardClient(forward_client);
			forward_client.setOutboundServer(out_server);

			// Create the OutBoundServer Table to for this session id
			if (inBoundServerTable.containsKey(session_id)) {				
				in_server = (InBoundServer) inBoundServerTable.get(session_id); 
				log.debug("Recovered server inbound buffers with sid: " + session_id);
			}
			else {				
				in_server = new InBoundServer();
				inBoundServerTable.put(session_id, in_server);
				log.debug("Add server inbound buffer with sid : " + session_id);	
			}
						
			in_server.setForwardClient(forward_client);
			forward_client.setInboundServer(in_server);

			//********************************************************************************************
			//                     /Initiliazing all tables for this SessionID 
			//********************************************************************************************
		
			/** TO BE REMOVED
			log.debug("Waiting for the last socket to finish: (" + remote_port + ")");	
			while(out_server.getBoundLocked()) {
				// Wait for the bound to be unlocked
				try {
					Thread.currentThread().sleep((long) 20);
				} catch (Exception e) {
				}
			}
			log.debug("Last socket released: (" + remote_port + ")");
			*/

			//out_server.setBoundLocked(true);
			String result = processPOST(mySocket, http_headers, http_arguments, remote_port, out_server, in_server, client_thread);
			log.debug("Result from POST processing: " + result);

			if (result.equals("cleanup")) {
				// Wait for the InBoundServer to send the CLOSE flag	
				while(!in_server.getStopFlagSent()) {
					try {
						in_server.setSendClose(true);
						Thread.currentThread().sleep((long) 20);
					} catch (Exception e) {
					}
				}
				/** TO BE REMOVED
				// Close any Thread with session_id reference
				while(anyThreadRunning(session_id)) {
					try {
						killThreadRunning(session_id);
						Thread.currentThread().sleep((long) 20);
					} catch (Exception e) {
					}
				}
							
				cleanupTables();
				*/
				thread_result = "cleanup:" + session_id;
			}

			//out_server.setBoundLocked(false);
			close(mySocket);
			break;

			// Until here we have finished processing POST calls

		case "get":

			log.info("GET method called");
			
			// Renaming the Thread to ease cleaning-up tasks
			th_name = Thread.currentThread().getName();
			if (th_name.contains(session_id))
				Thread.currentThread().setName(th_name.split(":")[0] + ":" + session_id + "-get");
			else
				Thread.currentThread().setName(th_name + ":" + session_id + "-get");

			//************************************************************************************
			//                     Waiting for the following conditions to be met
			//  - OutBoundServerTable contains the corresponding object to its SessionID.
			//  - OutBoundServer object on the table is not null.
			//  - InBoundServerTable contains the corresponding object to its SessionID.
			//  - InBoundServer object on the table is not null.
			//  - ClientsTable contains the corresponding object to its SessionID.
			//  - ForwardClient object on the table is not null.
			//************************************************************************************

			while ((!outBoundServerTable.containsKey(session_id)) ||
				   (outBoundServerTable.get(session_id) == null) ||
				   (!inBoundServerTable.containsKey(session_id)) ||
				   (inBoundServerTable.get(session_id) == null) ||
				   (!clientsTable.containsKey(session_id)) ||
				   (clientsTable.get(session_id) == null)
				  ) {
					
				try {
					Thread.currentThread().sleep((long) 20);
				} catch (Exception e) {
				}
				
			}
			
			log.debug("Recovered In Table: " + inBoundServerTable.toString());
			log.debug("Recovered Out Table: " + outBoundServerTable.toString());
			log.debug("Recovered Client Table: " + clientsTable.toString());
			
			in_server = (InBoundServer) inBoundServerTable.get(session_id);
			out_server = (OutBoundServer) outBoundServerTable.get(session_id);
			forward_client = (ForwardClient) clientsTable.get(session_id); 

			// We wait also until the tunnel is opened to start processing the GET request
			
			while (!out_server.getTunnelOpened() ||
				   in_server.getBoundLocked()) {
				try {
					log.debug("Tunnel Opened Value (out_server): " + out_server.getTunnelOpened());
					log.debug("Tunnel Bound Locked: (in_server): " + in_server.getBoundLocked());
					Thread.currentThread().sleep((long) 2000);
				} catch (Exception e) {
				}
			}
				  

			/** ===========================================================================
			 * This part is going to be deleted due it is not needed
			 * 
			while (!clientsTable.containsKey(session_id)) {
				// Waiting for the forward client to start
				try {
					Thread.currentThread().sleep((long) 10);
					if (!isThreadRunning(session_id)) {
						sendClientCloseSignal(mySocket);
						break;
					}
				} catch (Exception e) {
				}
			}
			printThreadRunning();
			log.debug("Client Table: " + clientsTable.toString());	
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
			** ===========================================================================
			*/

			in_server.setBoundLocked(true);

			//****************************************************************************
			//   The reasun that processGET does not return any value is due the error 
			//   actions are being processed by POST threads
			//****************************************************************************

			processGET(mySocket, http_headers, http_arguments, in_server, out_server);
			in_server.setBoundLocked(false);
			close(mySocket);
			
			break;

		case "head":
			log.info("HEAD called");
			break;
		default:
			log.error("Method NOT allowed");
			break;
		}

		return thread_result;
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

	private String processPOST(MySocket socket, Hashtable<String, String> http_headers,
							Hashtable<String, String> http_arguments, int remote_port, 
							OutBoundServer out_server, InBoundServer in_server, 
							Thread client_thread) {
		
		byte[] buff = new byte[JHttpTunnel.BUFFER_LENGTH];
		byte controlbyte = 0;
		int temp = 0;
		int data_length = 0;
		boolean keep_request = true;
		int postTraffic = 0;
		String ret_value = "continue";
		//byte[] tempo = new byte [1]; 

		log.info("Starting POST processing: " + out_server.toString());
			
		// Initialise the buffer for this port

		out_server.initPort(remote_port);

		try {

			while ( keep_request && 
			        (!out_server.getSendClose())			
			      ) {
			
				if (out_server.getTunnelOpened()) {
					if (socket.read(buff, 0, 1) > 0) {
						//if (socket.available() > 0) {
							// Get the first control byte						
							// temp = socket.read(buff, 0, 1);						
							postTraffic++;
							controlbyte = buff[0];
							log.debug("Control Byte Received: " + controlbyte);
	
							switch (controlbyte & 0xFF) {
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
								ret_value = "continue";
								break;
	
							case JHttpTunnel.TUNNEL_CLOSE:
								keep_request = false;
								log.info("Closing the tunnel!!");
								out_server.setTunnelOpened(false);
								ret_value = "cleanup";
								in_server.setSendClose(true);					
								break;
							}
						//}
					}
				} else {
					if (socket.read(buff, 0, 1) > 0) {
						if (socket.available() > 0) {
							//temp = socket.read(buff, 0, 1);					
							postTraffic++;
							controlbyte = buff[0];
							log.debug("Tunnel not yet Opened, Control Byte Received: " + controlbyte);					
	
							if ((controlbyte & 0xFF) == JHttpTunnel.TUNNEL_OPEN) {
								temp = socket.read(buff, 0, 3);
								postTraffic += 3;
								out_server.setTunnelOpened(true);
								log.info("Tunnel Opened, Starting ForwardClient Thread");
								client_thread.start();
							}
						}
					}
				}
				log.debug("Socket available: " + socket.available());			
				Thread.currentThread().sleep((long) 2000); // Delay for the while loop
			
			}

			if (out_server.getSendClose()) {
				ret_value = "cleanup";
				in_server.setSendClose(true);				
			}

		//} catch (InterruptedException e) {
		//	throw e;
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("POST processing Errors: " + errors.toString());
			ret_value = "cleanup";
			in_server.setSendClose(true);
		} 

		log.info("About to CLOSE POST socket... ");
		out_server.removePort(remote_port);

		return ret_value;


		/** This part of the development is for removal
		try {
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
						log.info("Openning http tunnel");
						
					}
				}

				Thread.currentThread().sleep((long) 10);
			} while (keep_request && (!forward_client.isClosed()) && 
			         (!out_server.getSendClose()) && isThreadRunning(session_id));

			if (out_server.getSendClose())
				cleanupTables();

			log.info("About to CLOSE POST socket... ");
			out_server.removePort(remote_port);
			close(socket);

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("POST processing Errors: " + errors.toString());
		}
		*/

	}

	private void processGET(MySocket socket, Hashtable<String, String> http_headers,
			Hashtable<String, String> http_arguments, InBoundServer in_server, 
			OutBoundServer out_server) throws IOException {
				
		log.info("Starting GET processing: " + in_server.toString());
		byte[] buff = new byte[10240];
		byte[] data_length = new byte[2];
		int getTraffic = 0;
		boolean keep_request = true;
		int position = 0;
		//int correction = 0;
		ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(2);

		// =========================================================================
		// We initialise the scheduler to send PADs periodically

		final MySocket fsocket = socket;
		final BoundServer fin_server = in_server;
		final BoundServer fout_server = out_server;
		Runnable runnableSendPad = new Runnable() {
			byte[] pad = new byte[1];				
			@Override
			public void run() {
				pad[0] = JHttpTunnel.TUNNEL_PAD1;
				try {
				    fsocket.write(pad, 0, 1);
				    log.debug("Server PAD sent");
				    //fin_server.fcl_message();
				} catch (SocketException e){
					fin_server.setSendClose(true);
					fout_server.setSendClose(true);
					StringWriter errors = new StringWriter();
					e.printStackTrace(new PrintWriter(errors));
					log.error("Socket Exception Sending PAD: " + errors.toString());
				} catch (Exception e){
					StringWriter errors = new StringWriter();
					e.printStackTrace(new PrintWriter(errors));
					log.error("Error sending PAD: " + errors.toString());
				}
			}
		};
		scheduledPool.scheduleAtFixedRate(runnableSendPad, 5, 5, TimeUnit.SECONDS);
		// =========================================================================

		log.info("Starting GET processing: " + forward_client.toString());
		
		try {		
			sendok(socket);
		
			while (keep_request &&
				   !(in_server.getSendClose())
				  ) {								
					
				if (in_server.nextBufferData() != 0) {

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
	
					//log.debug("Getting byte length of: " + Arrays.toString(tmp));
					data_length = getDataLength(tmp.length);
					socket.write(data_length, 0, 2);
					getTraffic += 2;
	
					socket.write(tmp, 0, tmp.length);
					getTraffic += tmp.length;
						
					log.debug("GET Data Traffic: " + getTraffic + " | Length: " + 
					          tmp.length + " | Continue Tunnel Flag: " + keep_request);
				}
				try {
					Thread.currentThread().sleep((long) 20); // Delay for the while loop
				}
				catch (InterruptedException e) {
					throw e;
				}
			}
		
			if (!keep_request) {
				buff[0] = JHttpTunnel.TUNNEL_DISCONNECT;			
				socket.write(buff, 0, 1);					
				getTraffic++;
			}
	
			if (in_server.getSendClose()) {
				log.debug("Closing the socket and sending CLOSE signal");
				sendClientCloseSignal(in_server, socket);
				out_server.setSendClose(true);
				getTraffic++;
			}
			// Stopping the scheduled thread
			
			scheduledPool.shutdown();
			log.info("About to CLOSE GET socket... ");
			
		} catch (Exception e) {
			scheduledPool.shutdown();
			sendClientCloseSignal(in_server, socket);
			out_server.setSendClose(true);
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("GET Processing Errors: " + errors.toString());
		}	
		

		/** TO BE REMOVED

		try {
			in_server.fcl_setGETLocked(true);
			log.info("Starting GET processing: " + forward_client.toString());

			sendok(socket);
			getTraffic = correction;
			
			// Start the PAD sent only if the tunnel is opened
			while (!in_server.getTunnelOpened()) {
				Thread.currentThread().sleep((long) 10);
				log.debug("Waiting for the tunnel to be opened");
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
					sendClientCloseSignal(socket);
					keep_request = false;
					getTraffic++;
				}

				// Do nothing
				Thread.currentThread().sleep((long) 10);

			} while (keep_request && (!in_server.fcl_isClosed()) && isThreadRunning(session_id));
			
			// Stopping scheduler
			if (in_server.getSendClose())
				cleanupTables();
			scheduledPool.shutdown();
			log.info("About to CLOSE GET socket... ");
			close(socket);
			in_server.fcl_setGETLocked(false);				
			
		} catch (InterruptedException e) {
			log.error("Thread interrupted");
			cleanupTables();
			scheduledPool.shutdown();			
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("GET Processing Errors: " + errors.toString());
		}
		*/
	}

	private void close(MySocket socket) {
		// Closing the thread
		while (!socket.isClosed()) {
			socket.close();
		}
	}

	private void sendClientCloseSignal(InBoundServer in_server, MySocket socket) throws IOException {
		byte[] close = new byte[1];
		log.debug("Sending CLOSE to the client!: " + Arrays.toString(close));
		in_server.setStopFlagSent(true);	
		close[0] = JHttpTunnel.TUNNEL_CLOSE;
		socket.write(close, 0, 1);
		//cleanupTables();
		close(socket);
	}

	private void cleanupTables() {
		BoundServer out_server = (BoundServer) outBoundServerTable.get(session_id);
		BoundServer in_server = (BoundServer) inBoundServerTable.get(session_id);
		ForwardClient client = (ForwardClient) clientsTable.get(session_id);
		out_server = null;
		in_server = null;
		client = null;	
		outBoundServerTable.remove(session_id);
		inBoundServerTable.remove(session_id);
		clientsTable.remove(session_id);
		log.debug("Cleaning up SessionID [" + session_id + "] tables");
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
		cleanupTables();
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

	/** To be removed
	private boolean anyThreadRunning(String t_id) {
		boolean ret_value = false;
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		Iterator<Thread> threadIt = threadSet.iterator();
		while (threadIt.hasNext()) {
			Thread thread = (Thread) threadIt.next();
			if (thread.getName().contains(t_id))
				ret_value = true;
		}
		return ret_value;
	}

	private void killThreadRunning(String t_id) {		
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		Iterator<Thread> threadIt = threadSet.iterator();
		while (threadIt.hasNext()) {
			Thread thread = (Thread) threadIt.next();
			if (thread.getName().contains(t_id)) {
				while (thread.isAlive()) {
					log.debug("Killing thread: " + thread.getName());
					thread.interrupt();
				}
			}			
		}
	}

	private void printThreadRunning() {
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		Iterator<Thread> threadIt = threadSet.iterator();
		while (threadIt.hasNext()) {
			Thread thread = (Thread) threadIt.next();
			log.debug("---> Thread Running: " + thread.getName());
		}
	}
	*/
}

