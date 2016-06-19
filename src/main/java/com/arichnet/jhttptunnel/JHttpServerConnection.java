package com.arichnet.jhttptunnel;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

class JHttpServerConnection {
	private MySocket mySocket = null;
	private String forward_host = null;
	private int forward_port = 0;
	private String session_id = "";
	private final String rootDirectory = ".";
	private final String defaultFile = "index.html";
	private ForwardClient forward_client = null;
	private OutBoundServer out_server = null;
	private Hashtable<String, ForwardClient> clientsTable = null;
	private Hashtable<String, BoundServer> outBoundServerTable = null;
	private List<Integer> postRemotePorts = null;
	private boolean tunnel_already_opened = false;
	private DateFormat date_format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

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
	
	JHttpServerConnection(Socket s, String h, int p, Hashtable T, List l, Hashtable o) throws IOException {
		super();
		mySocket = new MySocket(s);
		forward_host = h;
		forward_port = p;
		clientsTable = T;
		postRemotePorts = l;
		outBoundServerTable = o;
	}

	public void newsocket() {
		String socket_readline = "";
		String http_method = "";
		String temp = "";

		Hashtable<String, String> http_headers = new Hashtable<String, String>();
		Hashtable<String, String> http_arguments = new Hashtable<String, String>();

		System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
				           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + 
				           "] New TCP Connection started");

		socket_readline = mySocket.readLine();
		System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
                           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + 
                           "] Line Read: " + socket_readline);
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
		System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
				           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + 
				           "] Method of this socket: " + http_method);

		http_headers = getHttpHeader(mySocket);

		switch (http_method.toLowerCase()) {
		case "post":
			// Create the OutBoundServer Table to reflect this session id
			if (outBoundServerTable.containsKey(session_id)) {
				System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
				           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() 
				           + "] Recovering server outbound buffers with sid: " + session_id);
				out_server = (OutBoundServer) outBoundServerTable.get(session_id); 
			}
			else {				
				out_server = new OutBoundServer();
				outBoundServerTable.put(session_id, out_server);
				System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
				           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() 
				           + "] Add server outbound buffer with sid : " + session_id);
			}
			
			Integer remote_port = new Integer(mySocket.getRemotePort());
			postRemotePorts.add(remote_port);
			System.out.println( "[" + date_format.format(Calendar.getInstance().getTime()) + "] " + 
				                "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() +
				                "] Remote Ports List: " + Arrays.toString(postRemotePorts.toArray()));
			
			
			// *****************   FIX THIS CODE   *********************************
			// ********************  BELOW *****************************************
			
			
			
			// Get the session id client if it has been initiated
			if (clientsTable.containsKey(session_id)) {
				System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
						           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() 
						           + "] Recovering client with sid: " + session_id);
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
				System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
						           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() 
						           + "] Adding client to the table " + clientsTable.containsKey(session_id));
				tunnel_already_opened = false;
				// forward_client.message();
			}

			// forward_client.setForwardClientData(forward_host, forward_port,
			// session_id);
			// Start the client with the Session ID specified

			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() 
					           + "] POST called: " + forward_client.toString());

			// Wait until POST is unlocked
			
			/** THERE IS NO NEED TO LOCK FORWARD CLIENT
			 *  TO BE REMOVED 
			 * 
			 
			while (forward_client.getPOSTlocked()) {
				try {
					Thread.currentThread().sleep((long) 1);
				} catch (Exception e) {
				}
			}
			forward_client.setPOSTlocked(true);
			*/
			processPOST(mySocket, http_headers, http_arguments, forward_client, tunnel_already_opened, remote_port, out_server);
			
			/** THERE IS NO NEED TO LOCK FORWARD CLIENT
			 *  TO BE REMOVED 
			 *
			forward_client.setPOSTlocked(false);
			*/
			postRemotePorts.remove(remote_port);
			
			
			
			// ********************  ABOVE *****************************************
			// *****************   FIX THIS CODE   *********************************
			
			

			break;

		case "get":
			
			
			
			// *****************   FIX THIS CODE   *********************************
			// ********************  BELOW *****************************************
			
			
			
			while (!clientsTable.containsKey(session_id)) {
				// Waiting for the forward client to start
				try {
					Thread.currentThread().sleep((long) 10);
				} catch (Exception e) {
				}
			}

			forward_client = (ForwardClient) clientsTable.get(session_id);
			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] GET called: " + forward_client.toString());

			// Wait until GET is unlocked
			while (forward_client.getGETlocked()) {
				try {
					Thread.currentThread().sleep((long) 1);
				} catch (Exception e) {
				}
			}

			forward_client.setGETlocked(true);
			processGET(mySocket, http_headers, http_arguments, forward_client);
			
			
			
			
			// ********************  ABOVE *****************************************
			// *****************   FIX THIS CODE   *********************************
			
			
			
			
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
		// Vector return_data = new Vector();
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
			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] HEADERS (Key:Value) " + 
					           key + ":" + value);
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
					System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
							           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] ARGUMENTS (Key:Value) " + 
							           key + ":" + value);
				} while (URL.indexOf("&") != -1);
			}
		} catch (Exception e) {
			return_value = null;
			System.out.println("Exception occured: " + e.toString());
		}
		return return_value;
	}

	private void processPOST(MySocket socket, Hashtable<String, String> http_headers,
			Hashtable<String, String> http_arguments, ForwardClient forward_client, boolean tunnel_already_opened,
			int remote_port, OutBoundServer out_server) {

		
		
		// *****************   FIX THIS CODE   *********************************
		// ********************  BELOW *****************************************

		
		
		
		byte[] buff = new byte[JHttpTunnel.BUFFER_LENGTH];
		try {
			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() 
					           + "] Starting POST processing: " + forward_client.toString());
			byte controlbyte = 0;
			int temp = 0;
			boolean tunnel_opened = tunnel_already_opened;
			int data_length = 0;
			boolean keep_request = true;
			int postTraffic = 0;
			// int delta = 3; // because is the minimum needed for data
			/** TO BE REMOVED
			ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(4);
			*/
			
			// Initialize the buffer for this port
			out_server.initPort(remote_port);

			do {

				if (forward_client.getTunnelOpened()) {

					if (socket.available() > 0) {
						// Get the first control byte
						temp = socket.read(buff, 0, 1);
						postTraffic++;
						controlbyte = buff[0];
						System.out.println("[" + date_format.format(Calendar.getInstance().getTime())
		                   				   + "] " + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Control Byte Received: "
		                   				   + controlbyte);

						switch (controlbyte) {
						case JHttpTunnel.TUNNEL_DATA:
							temp = socket.read(buff, 0, 2);
							postTraffic += 2;
							data_length = ((buff[0] & 0xFF) << 8) + (buff[1] & 0xFF);
							// Check if the size is higher than the limit
							// Stop get or post after processing this data
							if ((data_length + postTraffic) > JHttpTunnel.CONTENT_LENGTH) {
								keep_request = false;
								System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
										+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] BREAK Flag: " + keep_request);
							}

							System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
									+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] POST Data Traffic: " + postTraffic
									+ " | POST Data Length: " + data_length + " | BREAK Flag: " + keep_request);

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
									
									/**
									System.out.println("[" + date_format.format(Calendar.getInstance().getTime())
									                   + "] " + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Buff size: "
									                   + buff.length + " | Data length: " + data_length);
									*/
								}
								/**
								if (forward_client.isBufferOutAvailable()) {
									if (data_length > 10240) {
										socket.read(buff, 0, 10240);
										postTraffic += 10240;
										forward_client.writeOutputBuffer(buff);
										data_length -= 10240;
									} else {
										temp = socket.read(buff, 0, data_length);
										postTraffic += data_length;
										System.out.println("[" + date_format.format(Calendar.getInstance().getTime())
												+ "] " + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Buff size: "
												+ buff.length + " | Data length: " + data_length);
										forward_client.writeOutputBuffer(Arrays.copyOfRange(buff, 0, data_length));
										data_length = 0;
									}
									forward_client.unlockOutputBuffer();
								}
								*/
							} while (data_length > 0);
							// Print the status of the buffers
							System.out.println("[" + date_format.format(Calendar.getInstance().getTime())
			                                   + "] " + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] * Buff Status: "
			                                   + out_server);
							break;

						case JHttpTunnel.TUNNEL_PAD1:
							System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
									+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Server PAD received");
							break;

						case JHttpTunnel.TUNNEL_DISCONNECT:
							keep_request = false;
							System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
									+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Disconnecting the tunnel!! ");
							forward_client.message();							
							// timer.cancel();
							// scheduledPool.shutdown();
							break;

						case JHttpTunnel.TUNNEL_CLOSE:
							keep_request = false;
							System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
									+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Closing the tunnel!! ");
							forward_client.setCONTROL(JHttpTunnel.TUNNEL_CLOSE);
							//tunnel_opened = false;
							forward_client.setTunnelOpened(false);
							closeForwardClient(forward_client);
							forward_client.message();
							
							/** TO BE REMOVED
							scheduledPool.shutdown();
							*/
														
							// timer.cancel();
							// forward_client.close();
							break;
						}
					}

				} else {
					temp = socket.read(buff, 0, 1);
					postTraffic++;
					controlbyte = buff[0];

					if ((controlbyte & 0xFF) == JHttpTunnel.TUNNEL_OPEN) {
						temp = socket.read(buff, 0, 3);
						postTraffic += 3;
						forward_client.setTunnelOpened(true);
						//tunnel_opened = true;
						System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
								+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Openning http tunnel");
						// Starting Timer to send PADs
						//
						/** TO BE REMOVED

						final ForwardClient fforward_client = forward_client;
						Runnable runnableSendPad = new Runnable() {
							@Override
							public void run() {
								fforward_client.setCONTROL(JHttpTunnel.TUNNEL_PAD1);
								System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
										+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Server PAD sent");
							}
						};
						scheduledPool.scheduleAtFixedRate(runnableSendPad, 5, 5, TimeUnit.SECONDS);
						*/
					}
				}

				Thread.currentThread().sleep((long) 10);
			} while (keep_request && (!forward_client.isClosed()));

			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] ABOUT TO CLOSE POST SOCKET... ");
			out_server.removePort(remote_port);
			close(socket);
			// System.out.println("Exiting: ");

			
			
			// ********************  ABOVE *****************************************
			// *****************   FIX THIS CODE   *********************************

			
			

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));			
			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					           + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] POST processing Errors: " 
					           + errors.toString());
		}
	}

	private void processGET(MySocket socket, Hashtable<String, String> http_headers,
			Hashtable<String, String> http_arguments, ForwardClient forward_client) {
		

		
		// *****************   FIX THIS CODE   *********************************
		// ********************  BELOW *****************************************
		
		
		
		byte[] buff = new byte[10240];
		byte[] data_length = new byte[2];
		int getTraffic = 0;
		boolean keep_request = true;
		int position;
		int correction = 0;
		ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(4);

		try {
			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Starting GET processing: " + forward_client.toString());

			sendok(socket);
			getTraffic = correction;
			
			// Start the PAD sent only if the tunnel is opened
			while (!forward_client.getTunnelOpened()) {
				Thread.currentThread().sleep((long) 10);
			}
			
			//We initialized the PAD send
			final MySocket fsocket = socket;
			Runnable runnableSendPad = new Runnable() {
				byte[] pad = new byte[1];				
				@Override
				public void run() {
					pad[0] = JHttpTunnel.TUNNEL_PAD1;
					try {
					    fsocket.write(pad, 0, 1);
					    System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
							   + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Server PAD sent");
					}
					catch (Exception e){
						System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
								   + "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Error sending PAD");
					}
				}
			};
			scheduledPool.scheduleAtFixedRate(runnableSendPad, 5, 5, TimeUnit.SECONDS);

			do {

				if ((forward_client.getBufferInPosition() > 0) && !forward_client.getBufferInLocked()) {
					if ((forward_client.getBufferInPosition() + getTraffic + 3) > (JHttpTunnel.CONTENT_LENGTH - 3)) {
						position = JHttpTunnel.CONTENT_LENGTH - getTraffic - 3;
						keep_request = false;

					} else {
						position = forward_client.getBufferInPosition();
					}

					byte[] tmp = forward_client.readInputBuffer(position);

					buff[0] = JHttpTunnel.TUNNEL_DATA;
					socket.write(buff, 0, 1);
					getTraffic++;
					data_length = getDataLength(tmp.length);
					socket.write(data_length, 0, 2);
					getTraffic += 2;
					socket.write(tmp, 0, tmp.length);
					getTraffic += tmp.length;

					System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
							+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] GET Data Traffic: " + getTraffic + " | Length: "
							+ tmp.length + " | BREAK Flag: " + keep_request);

				}

				if (forward_client.getCONTROL()[0] != 0) {
					socket.write(forward_client.getCONTROL(), 0, 1);
					forward_client.setCONTROL((byte) 0);
					getTraffic++;
				}
				// Thread.currentThread().sleep((long) 1);

				if (!keep_request) {
					// Thread.currentThread().sleep((long) 1);
					buff[0] = JHttpTunnel.TUNNEL_DISCONNECT;
					socket.write(buff, 0, 1);
					getTraffic++;
				}

				else {
					// Thread.currentThread().sleep((long) 1);
				}

				// Do nothing
				Thread.currentThread().sleep((long) 10);

			} while (keep_request && (!forward_client.isClosed()));
			
			// Stopping scheduler
			scheduledPool.shutdown();
			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] ABOUT TO CLOSE GET SOCKET... ");
			close(socket);
			forward_client.setGETlocked(false);

			
			
		
		// ********************  ABOVE *****************************************	
	    // *****************   FIX THIS CODE   *********************************
			
			
			
			
			
			
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
					+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] GET Processing Errors: " + errors.toString());
		}
	}

	private void close(MySocket socket) {
		// Closing the thread
		while (!socket.isClosed()) {
			socket.close();
		}
	}

	private void closeForwardClient(ForwardClient forward_client) {
		
		
		
		// *****************   FIX THIS CODE   *********************************
		// ********************  BELOW *****************************************
		
		
		// Remove the client from client hash table
		clientsTable.remove(session_id);
		// Close the Forward Client
		forward_client.close();
		
		
		
		// ********************  ABOVE *****************************************
		// *****************   FIX THIS CODE   *********************************		

		
		
	}

	private void sendok(MySocket socket) throws IOException {
		System.out.println("[" + date_format.format(Calendar.getInstance().getTime()) + "] "
				+ "[" + Thread.currentThread().getName() + "|" + this.getClass().getName() + "] Sending GET OK");
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
	
	/** TO BE REMOVED

	private boolean existLowerRemotePort(int remote_port) {
		boolean return_value = false;
		for (Integer port: postRemotePorts) {
			if (port.intValue() < remote_port)
				return_value = true;			
		}		
		return return_value;
	}
	
	*/
}

