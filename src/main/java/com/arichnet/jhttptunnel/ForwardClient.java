package com.arichnet.jhttptunnel;

import java.net.*;
import java.io.*;
import java.nio.*;

public class ForwardClient implements Runnable {
	String forward_host = "127.0.0.1";
	String session_id = "";
	int forward_port = 0;
	Socket forward_socket = null;
	InputStream forward_in = null;
	OutputStream forward_out = null;
	ByteBuffer buffer_in = ByteBuffer.allocateDirect(10240);
	ByteBuffer buffer_out = ByteBuffer.allocateDirect(1024);
	byte[] _rn = "\r\n".getBytes();
	byte[] control = new byte[1];
	boolean GETlocked = false;
	boolean POSTlocked = false;
	boolean buffer_in_locked = false;
	// ByteArrayInputStream buffer_in = new ByteArrayInputStream(new
	// byte[1024]);
	// ByteArrayOutputStream buffer_out = new ByteArrayOutputStream();

	public ForwardClient(String fhost, int fport, String sid) {
		forward_host = fhost;
		forward_port = fport;
		session_id = sid;
		control[0] = 0;
		buffer_in_locked = false;
	}

	public ForwardClient() {
		forward_host = "127.0.0.1";
		forward_port = 22;
		session_id = "123456";
		control[0] = 0;
		buffer_in_locked = false;
	}

	public void setForwardClientData(String h, int p, String s) {
		forward_host = h;
		forward_port = p;
		session_id = s;
		control[0] = 0;
	}

	@Override
	public void run() {
		try {
			System.out.println("Thread: " + Thread.currentThread().getName() + "¡¡¡¡¡ Starting forward client: "
					+ this.toString());
			// Connect to forward server
			byte[] data_to_send = new byte[1024];
			byte[] data_to_receive = new byte[10240];
			int data_size = 0;

			forward_socket = new Socket(forward_host, forward_port);
			forward_socket.setTcpNoDelay(true);

			forward_in = forward_socket.getInputStream();
			forward_out = forward_socket.getOutputStream();

			message();

			while (forward_socket.isConnected()) {
				if (forward_in.available() > 0) {
					data_size = forward_in.available();
					do {
						if ((!buffer_in_locked) && (getBufferInPosition() == 0)) {
							System.out.println("Thread: " +	Thread.currentThread().getName() +
							        " | ForwardIN available: " + forward_in.available() +
							        " | Buffer Position: " + getBufferInPosition());
							if (data_size >= 10240) {
								forward_in.read(data_to_receive, 0, 10240);
								try {
								   buffer_in.put(data_to_receive, 0, 10240);
								}
								catch (BufferOverflowException e) {
									System.out.println("ERROR OF OVERRFLOW DETECTED");									
									System.out.println("Data to insert: 10240");									
									System.out.println("Actual Position: " + buffer_in.position());				
									StringWriter errors = new StringWriter();
									e.printStackTrace(new PrintWriter(errors));
									System.out.println("Thread: " + Thread.currentThread().getName() + 
											           " | ForwardClient error: " + errors.toString());
								}
								data_size -= 10240;
							} else {
								forward_in.read(data_to_receive, 0, data_size);
								buffer_in.put(data_to_receive, 0, data_size);
								// buffer_out.put(_rn, 0, 2);
								data_size = 0;
								// buffer_in.put(_rn, 0, 2);
							}
						}
						Thread.currentThread().sleep((long) 1);
					} while (data_size > 0);

				}
				if (buffer_out.position() > 0) {
					data_size = buffer_out.position();
					buffer_out.position(0);
					buffer_out.get(data_to_send, 0, data_size);

					// System.out.println("Thread: " +
					// Thread.currentThread().getName() +
					// " | **** SENDING DATA SIZE: " + data_size);
					forward_out.write(data_to_send, 0, data_size);
					buffer_out.rewind();
				}
				Thread.currentThread().sleep((long) 1);
			}
		} catch (IOException e) {
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			System.out.println(
					"Thread: " + Thread.currentThread().getName() + " | ForwardClient error: " + errors.toString());
		}

	}

	public byte[] readInputBuffer(int position) {
		byte[] return_data = null;
		byte[] tmp = null;
		// Lock buffer to avoid overwritte
		buffer_in_locked = true;

		int currentPosition = buffer_in.position();
		if (position < currentPosition) {
			return_data = new byte[position];
			buffer_in.rewind();
			buffer_in.get(return_data, 0, position);
			tmp = new byte[currentPosition - position];
			buffer_in.get(tmp);
			buffer_in.rewind();
			buffer_in.put(tmp);
		} else if (position == currentPosition) {
			return_data = new byte[currentPosition];
			buffer_in.rewind();
			try {
				buffer_in.get(return_data);
			}
			catch (BufferUnderflowException e) {
				System.out.println("ERROR OF UNDERFLOW DETECTED");
				System.out.println("Requested Pointer: " + position);
				System.out.println("Pointer: " + currentPosition);
				System.out.println("Actual Position: " + buffer_in.position());				
				StringWriter errors = new StringWriter();
				e.printStackTrace(new PrintWriter(errors));
				System.out.println("Thread: " + Thread.currentThread().getName() + 
						           " | ForwardClient error: " + errors.toString());
			}
			buffer_in.rewind();
		}
		
		buffer_in_locked = false;
		return return_data;
	}

	public int getBufferInPosition() {
		return buffer_in.position();
	}

	public int getBufferOutPosition() {
		return buffer_out.position();
	}

	public void writeOutputBufferln(byte[] bytes_data) {
		// System.out.println("Buffer status: " + buffer_out.position());
		writeOutputBuffer(bytes_data);
		buffer_out.put(_rn, 0, 2);
		// System.out.println("Thread: " + Thread.currentThread().getName() +
		// " | Byte Output Stream Size + Enter: " + bytes_data.length);
	}

	public void writeOutputBuffer(byte[] bytes_data) {
		// System.out.println("Buffer status: " + buffer_out.position());
		buffer_out.put(bytes_data, 0, bytes_data.length - 1);
		System.out.println(
				"Thread: " + Thread.currentThread().getName() + " | *** Byte Output Stream Size: " + bytes_data.length);
	}

	public void sendPAD1() {
		//buffer_in.put(JHttpTunnel.TUNNEL_PAD1);
		control[0] = JHttpTunnel.TUNNEL_PAD1;
	}

	public void sendCLOSE() {
		//buffer_in.put(JHttpTunnel.TUNNEL_CLOSE);
		control[0] = JHttpTunnel.TUNNEL_CLOSE;
	}
	
	public void zeroCONTROL() {
		control[0] = 0;
	}
	
	public byte[] getCONTROL() {
		return control;
	}

	public boolean isClosed() {
		boolean return_value = false;
		if (forward_socket != null)
			return_value = forward_socket.isClosed();
		return return_value;
	}

	public void close() {
		try {
			while (!forward_socket.isClosed()) {
				System.out.println("******+++ CLOSING FORWARD COMM ++++*******");
				forward_socket.close();
			}
			while (!Thread.currentThread().interrupted()) {
				Thread.currentThread().interrupt();
			}
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			System.out.println(
					"Thread: " + Thread.currentThread().getName() + " | ForwardClient error: " + errors.toString());
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

	public void message() {
		System.out.println("Thread: " + Thread.currentThread().getName() + " | ForwardClient MESSAGE request, "
				+ " Buffer IN, " + buffer_in.toString() + ", " + " Buffer OUT: " + buffer_out.toString() + ", "
				+ " Socket INFO: " + forward_socket.toString());
	}

	public String getThreadID() {
		return Thread.currentThread().getName();
	}
}
