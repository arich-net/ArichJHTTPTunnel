package com.arichnet.jhttptunnel;

import java.io.*;
import java.net.*;
import java.util.*;

class JHttpServerConnection {
	private MySocket mySocket = null;
	private String forward_host = null;
	private int forward_port = 0;
	private String session_id = "";
	private final String rootDirectory = ".";
	private final String defaultFile = "index.html";
	private ForwardClient forward_client = null;
	private Hashtable<String, ForwardClient> clientsTable = null;
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

	public void newsocket() {
		String socket_readline = "";
		String http_method = "";
		String temp = "";

		Hashtable<String, String> http_headers = new Hashtable();
		Hashtable<String, String> http_arguments = new Hashtable<String, String>();

		System.out.println("Thread: " + Thread.currentThread().getName() + " | New TCP Connection started");

		socket_readline = mySocket.readLine();
		System.out.println(
				"Thread: " + Thread.currentThread().getName() + " | First line data received: " + socket_readline);
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
		System.out.println("Thread: " + Thread.currentThread().getName() + " | Method of this socket: " + http_method);

		http_headers = getHttpHeader(mySocket);

		switch (http_method.toLowerCase()) {
		case "post":
			// Get the session id client if it has been initiated
			if (clientsTable.containsKey(session_id)) {
				System.out.println(
						"Thread: " + Thread.currentThread().getName() + " | Recovering client with sid: " + session_id);
				forward_client = (ForwardClient) clientsTable.get(session_id);
				forward_client.message();
				tunnel_already_opened = true;
			} else {
				forward_client = new ForwardClient();
				forward_client.setForwardClientData(forward_host, forward_port, session_id);
				Thread client_thread = new Thread(forward_client, session_id);
				client_thread.start();
				// Saving forward_client on the hash
				clientsTable.put(session_id, forward_client);
				System.out.println("Thread: " + Thread.currentThread().getName() + " | Adding client to the table "
						+ clientsTable.containsKey(session_id));
				tunnel_already_opened = false;
				// forward_client.message();
			}

			// forward_client.setForwardClientData(forward_host, forward_port,
			// session_id);
			// Start the client with the Session ID specified

			System.out.println(
					"Thread: " + Thread.currentThread().getName() + " | POST called: " + forward_client.toString());

			// Wait until POST is unlocked
			while (forward_client.getPOSTlocked()) {
				try {
					Thread.currentThread().sleep((long) 1);
				} catch (Exception e) {
				}
			}
			forward_client.setPOSTlocked(true);
			processPOST(mySocket, http_headers, http_arguments, forward_client, tunnel_already_opened);
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
			System.out.println(
					"Thread: " + Thread.currentThread().getName() + " | GET called: " + forward_client.toString());

			// Wait until GET is unlocked
			while (forward_client.getGETlocked()) {
				try {
					Thread.currentThread().sleep((long) 1);
				} catch (Exception e) {
				}
			}
			
			forward_client.setGETlocked(true);
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
			System.out.println(
					"Thread: " + Thread.currentThread().getName() + " | HEADERS (Key:Value) " + key + ":" + value);
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
					System.out.println("Thread: " + Thread.currentThread().getName() + " | ARGUMENTS (Key:Value) " + key
							+ ":" + value);
				} while (URL.indexOf("&") != -1);
			}
		} catch (Exception e) {
			return_value = null;
			System.out.println("Exception occured: " + e.toString());
		}
		return return_value;
	}

	private void processPOST(MySocket socket, Hashtable<String, String> http_headers,
			Hashtable<String, String> http_arguments, ForwardClient forward_client, boolean tunnel_already_opened) {

		byte[] buff = new byte[10240];
		try {
			System.out.println("Thread: " + Thread.currentThread().getName() + " | Starting POST processing: "
					+ forward_client.toString());
			byte controlbyte = 0;
			int temp = 0;
			boolean tunnel_opened = tunnel_already_opened;
			int data_length = 0;
			boolean keep_request = true;
			int postTraffic = 0;

			do {

				if (tunnel_opened) {

					if (socket.available() > 0) {
						// Get the first control byte
						temp = socket.read(buff, 0, 1);
						postTraffic++;
						controlbyte = buff[0];
						
						switch(controlbyte) {
						case JHttpTunnel.TUNNEL_DATA:
							temp = socket.read(buff, 0, 2);
							postTraffic += 2;
							data_length = ((buff[0] & 0xFF) << 8) + (buff[1] & 0xFF);
							// Check if the size is higher than the limit
							// Stop get or post after processing this data
							if ((data_length + postTraffic) > JHttpTunnel.CONTENT_LENGTH)
								keep_request = false;

							System.out.println("Thread: " + Thread.currentThread().getName() + 
									           " | POST Data Traffic: "	+ postTraffic +
									           " | POST Data Length: "	+ data_length +
									           " | BREAK Flag: " + keep_request);

							do {
								if (forward_client.getBufferOutPosition() == 0) {
									forward_client.lockOutputBuffer();
									if (data_length > 10240) {
										socket.read(buff, 0, 10240);
										postTraffic += 10240;
										forward_client.writeOutputBuffer(buff);
										data_length -= 10240;
									} else {
										temp = socket.read(buff, 0, data_length);
										postTraffic += data_length;
										forward_client.writeOutputBuffer(Arrays.copyOfRange(buff, 0, data_length));
										data_length = 0;
									}
									forward_client.unlockOutputBuffer();
								}
								Thread.currentThread().sleep((long) 1);
							} while (data_length > 0);
							continue;
						case JHttpTunnel.TUNNEL_PAD1:						
							forward_client.sendPAD1();
							continue;
						case JHttpTunnel.TUNNEL_DISCONNECT:
							keep_request = false;
							System.out.println(
									"Thread: " + Thread.currentThread().getName() + " | Disconnecting the tunnel!! ");
							forward_client.message();
							continue;
						case JHttpTunnel.TUNNEL_CLOSE:
							System.out.println(
									"Thread: " + Thread.currentThread().getName() + " | Closing the tunnel!! ");
							forward_client.sendCLOSE();
							tunnel_opened = false;
							closeForwardClient(forward_client);
							forward_client.message();
							// forward_client.close();
							continue;						
						}													
					}					
					if (((System.currentTimeMillis() / 1000) % JHttpTunnel.KEEP_ALIVE) == 0)
						forward_client.sendPAD1();
				} else {
					temp = socket.read(buff, 0, 1);
					postTraffic++;
					controlbyte = buff[0];

					if ((controlbyte & 0xFF) == JHttpTunnel.TUNNEL_OPEN) {
						temp = socket.read(buff, 0, 3);
						postTraffic += 3;
						tunnel_opened = true;
						System.out.println("Thread: " + Thread.currentThread().getName() + " | Openning http tunnel");
					}
				}

				Thread.currentThread().sleep((long) 1);
			} while (keep_request && (!forward_client.isClosed()));

			System.out.println("Thread: " + Thread.currentThread().getName() + " | ABOUT TO CLOSE POST SOCKET... ");

			close(socket);
			forward_client.setPOSTlocked(false);
			// System.out.println("Exiting: ");

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			System.out.println(
					"Thread: " + Thread.currentThread().getName() + " | POST processing Errors: " + errors.toString());
		}
	}

	private void processGET(MySocket socket, Hashtable<String, String> http_headers,
			Hashtable<String, String> http_arguments, ForwardClient forward_client) {
		byte[] buff = new byte[10240];
		byte[] data_length = new byte[2];
		int getTraffic = 0;
		boolean keep_request = true;
		int position;
		int correction = 0;

		try {
			System.out.println("Thread: " + Thread.currentThread().getName() + " | Starting GET processing: "
					+ forward_client.toString());

			sendok(socket);
			getTraffic = correction;

			do {

				if (forward_client.getBufferInPosition() > 0) {
					if ((forward_client.getBufferInPosition() + getTraffic + 3) > (JHttpTunnel.CONTENT_LENGTH -3)) {
						position = JHttpTunnel.CONTENT_LENGTH - getTraffic - 3;
						keep_request = false;
						
						System.out.println("Thread: " +	Thread.currentThread().getName() +
						        " | Fclient Position: " + forward_client.getBufferInPosition() +
						        " | Actual Position: " + position + " | Actual Traffic: " + getTraffic);
						
					} else {
						position = forward_client.getBufferInPosition();
						System.out.println("Thread: " +	Thread.currentThread().getName() +
						        " | TOTAL Fclient Position: " + forward_client.getBufferInPosition() +
						        " | TOTAL Actual Position: " + position + " | Actual Traffic: " + getTraffic);
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
					
				}
				if (forward_client.getCONTROL()[0] != 0) {
					socket.write(forward_client.getCONTROL(), 0, 1);
					forward_client.zeroCONTROL();
					getTraffic++;
				}

				if (!keep_request) {
					buff[0] = JHttpTunnel.TUNNEL_DISCONNECT;
					socket.write(buff, 0, 1);
					getTraffic++;
				}

				// Do nothing
				Thread.currentThread().sleep((long) 1);

			} while (keep_request && (!forward_client.isClosed()));

			System.out.println("Thread: " + Thread.currentThread().getName() + " | ABOUT TO CLOSE GET SOCKET... ");			
			close(socket);
			forward_client.setGETlocked(false);

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			System.out.println(
					"Thread: " + Thread.currentThread().getName() + " | GET Processing Errors: " + errors.toString());
		}
	}

	private void close(MySocket socket) {
		// Closing the thread
		while (!socket.isClosed()) {
			socket.close();
		}
	}

	private void closeForwardClient(ForwardClient forward_client) {
		// Remove the client from client hash table
		clientsTable.remove(session_id);
		// Close the Forward Client
		forward_client.close();

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
		return_data[0] = (byte) msb_int;
		return_data[1] = (byte) lsb_int;
		return return_data;
	}
}