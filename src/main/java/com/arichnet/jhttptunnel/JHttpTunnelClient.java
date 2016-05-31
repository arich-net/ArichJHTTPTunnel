/* -*-mode:java; c-basic-offset:2; -*- */
/*
 Copyright (c) 2004 ymnk, JCraft,Inc. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice,
 this list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in
 the documentation and/or other materials provided with the distribution.

 3. The names of the authors may not be used to endorse or promote products
 derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
 INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.arichnet.jhttptunnel;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

public class JHttpTunnelClient {
	// static final private int CONTENT_LENGTH=1024;
	static final private int CONTENT_LENGTH = 1024 * 10;

	private boolean init = false;
	private boolean closed = false;

	private String dest_host = null;
	private int dest_port = 0;
	private Proxy proxy = null;
	private int session_id = 1;

	private InBound ib = null;
	private OutBound ob = null;
	
	private Timer timer = new Timer();

	// private int sendCount=CONTENT_LENGTH;

	public JHttpTunnelClient(String host, int port) {
		this.dest_host = host;
		this.dest_port = port;
	}

	public JHttpTunnelClient(String host, int port, int sid) {
		this.dest_host = host;
		this.dest_port = port;
		this.session_id = sid;
	}

	public void setProxy(String host, int port) {
		this.proxy = new Proxy(host, port);
	}

	public void connect() throws JHttpTunnelException {

		if (ib == null) {
			/*
			 * try{ Class
			 * c=Class.forName("com.arichnet.jhttptunnel.InBoundSocket");
			 * ib=(InBound)c.newInstance(); } catch(Exception e){}
			 */
			throw new JHttpTunnelException("InBound is not given");
		}
		ib.setHost(dest_host);
		ib.setPort(dest_port);
		ib.setProxy(proxy);
		ib.setSid(session_id);

		if (ob == null) {
			/*
			 * try{ Class
			 * c=Class.forName("com.arichnet.jhttptunnel.OutBoundSocket");
			 * ob=(OutBound)c.newInstance(); } catch(Exception e){}
			 */
			throw new JHttpTunnelException("OutBound is not given");
		}
		ob.setHost(dest_host);
		ob.setPort(dest_port);
		ob.setProxy(proxy);
		ob.setContentLength(CONTENT_LENGTH);
		ob.setSid(session_id);

		try {
			getOutbound();
			getInbound();
		} catch (Exception e) {
			throw new JHttpTunnelException(e.toString());
		}
		
		final OutBound fob = ob;						
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run(){
				try {
					sendPad1(true);
					System.out.println(
							"Thread: " + Thread.currentThread().getName() + " | Client PAD sent");
				}
				catch(IOException e) {}
			}
		}, JHttpTunnel.KEEP_ALIVE * 1000, JHttpTunnel.KEEP_ALIVE * 1000);
	}

	private void getOutbound() throws IOException {
		// System.out.println("getOutbound()");
		if (closed) {
			throw new IOException("broken pipe");
		}
		ob.connect();
		if (!init) {
			openChannel(1);
			init = true;
		}
	}

	private void getInbound() throws IOException {
		// System.out.println("getInbound()");
		ib.connect();
	}

	private final byte[] command = new byte[4];

	public void openChannel(int i) throws IOException {
		// System.out.println("sendOpen: " + JHttpTunnel.TUNNEL_OPEN);
		// System.out.println("Stack Trace: " +
		// Thread.currentThread().getStackTrace()[2].getMethodName());

		command[0] = JHttpTunnel.TUNNEL_OPEN;
		command[1] = 0;
		command[2] = 1;
		command[3] = 0;
		ob.sendData(command, 0, 4, true);
	}

	public void sendDisconnect() throws IOException {
		// System.out.println("sendDisconnect: "+sendCount);
		command[0] = JHttpTunnel.TUNNEL_DISCONNECT;
		ob.sendData(command, 0, 1, true);
	}

	public void sendClose() throws IOException {
		System.out.println("Client sendClose: ");
		command[0] = JHttpTunnel.TUNNEL_CLOSE;
		ob.sendData(command, 0, 1, true);
	}

	public void sendPad1(boolean flush) throws IOException {
		command[0] = JHttpTunnel.TUNNEL_PAD1;
		ob.sendData(command, 0, 1, flush);
	}

	public void write(byte[] foo, int s, int l) throws IOException {
		System.out.println("write: l=" + l + ", sendCount=" + ob.sendCount);
		if (l <= 0)
			return;

		if (ob.sendCount <= 4) {
			System.out.println("ob.sendCount<=4: " + ob.sendCount);
			if (0 < ob.sendCount) {
				while (ob.sendCount > 1) {
					sendPad1(false);
				}
				sendDisconnect();
			}
			getOutbound();
		}

		while ((ob.sendCount - 1 - 3) < l) {
			int len = (ob.sendCount - 1 - 3);
			command[0] = JHttpTunnel.TUNNEL_DATA;
			command[1] = (byte) ((len >>> 8) & 0xff);
			command[2] = (byte) (len & 0xff);
			// System.out.println("send "+(len));
			ob.sendData(command, 0, 3, true);
			ob.sendData(foo, s, len, true);
			s += len;
			l -= len;

			// sendCount=1;

			sendDisconnect();
			if (l > 0) {
				getOutbound();
			}
		}
		if (l <= 0)
			return;

		command[0] = JHttpTunnel.TUNNEL_DATA;
		command[1] = (byte) ((l >>> 8) & 0xff);
		command[2] = (byte) (l & 0xff);
		ob.sendData(command, 0, 3, false);
		ob.sendData(foo, s, l, true);
	}

	int buf_len = 0;

	public int read(byte[] foo, int s, int l) throws IOException {
		System.out.printf("[%s] Read called: bytes=%d s=%d l=%d%n", Thread.currentThread().getName(), foo.length , s, l);
		if (closed)
			return -1;

		try { 
			if (buf_len > 0) {
				//**********************************************				
				
				int len = buf_len;
				len = (l < buf_len) ? l : buf_len;
				//if (l < buf_len) {
				//	len = l;
				//}
				int i = ib.receiveData(foo, s, len);
				buf_len -= i;
				System.out.println("¡¡buf_len="+buf_len);
				return i;
				
				//**********************************************
			}

			int len = 0;
			while (!closed) {
				int i = ib.receiveData(foo, s, 1);
				if (i <= 0) {
					return -1;
				}
				int request = foo[s] & 0xff;
				System.out.println("request: "+request);
				if ((request & JHttpTunnel.TUNNEL_SIMPLE) == 0) {
					i = ib.receiveData(foo, s, 1);
					len = (((foo[s]) << 8) & 0xff00);
					i = ib.receiveData(foo, s, 1);
					len = len | (foo[s] & 0xff);
				}
				//System.out.println("request: "+request);
				switch (request) {
				case JHttpTunnel.TUNNEL_DATA:
					buf_len = len;
					System.out.println("buf_len="+buf_len);
					/**
					if (l < buf_len) {						
						len = l;
					}
					**/
					int orgs = s;
					len = (l < buf_len) ? l : buf_len;
					
					
					while (len > 0) {
						//*****************************************
						
						
						System.out.printf("[%s] To be read i=%d len=%d s=%d%n", Thread.currentThread().getName(), i, len, s);												
						i = ib.receiveData(foo, s, len);
						if (i < 0) break;
						buf_len -= i;
						s += i;
						len -= i;
						System.out.printf("[%s] After read i=%d len=%d s=%d buf_len=%d%n", Thread.currentThread().getName(), i, len, s, buf_len);						

						
						
						//*****************************************
					}
					System.out.println("Received Data: "+(s-orgs));
					return s - orgs;
				case JHttpTunnel.TUNNEL_PADDING:
					ib.receiveData(null, 0, len);
					continue;
				case JHttpTunnel.TUNNEL_ERROR:
					byte[] error = new byte[len];
					ib.receiveData(error, 0, len);
					// System.out.println(new String(error, 0, len));
					throw new IOException("JHttpTunnel: " + new String(error, 0, len));
				case JHttpTunnel.TUNNEL_PAD1:
					continue;
				case JHttpTunnel.TUNNEL_CLOSE:
					closed = true;
					// close();
					System.out.println("CLOSE RECEIVED");
					break;
				case JHttpTunnel.TUNNEL_DISCONNECT:
					System.out.println("Received DISCONNECT!!!!!!... Trying to connect ib back");
					//closed = true;
					//ib.setHost(dest_host);
                                        //ib.setPort(dest_port);
                                        //ib.setProxy(proxy);
                                        //ib.setSid(session_id);
					continue;
				default:
					// System.out.println("request="+request);
					// System.out.println(Integer.toHexString(request&0xff)+
					// " "+new Character((char)request));
					throw new IOException("JHttpTunnel: protocol error 0x" + Integer.toHexString(request & 0xff));
				}
			}
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			System.out.println("JHttpTunnelClient.read: "+ errors.toString());
		}
		return -1;
	}

	private InputStream in = null;

	public InputStream getInputStream() {
		if (in != null)
			return in;
		in = new InputStream() {
			byte[] tmp = new byte[1];

			@Override
			public int read() throws IOException {
				int i = JHttpTunnelClient.this.read(tmp, 0, 1);
				return (i == -1 ? -1 : tmp[0]);
			}

			@Override
			public int read(byte[] foo) throws IOException {
				return JHttpTunnelClient.this.read(foo, 0, foo.length);
			}

			@Override
			public int read(byte[] foo, int s, int l) throws IOException {
				return JHttpTunnelClient.this.read(foo, s, l);
			}
		};
		return in;
	}

	private OutputStream out = null;

	public OutputStream getOutputStream() {
		if (out != null)
			return out;
		out = new OutputStream() {
			final byte[] tmp = new byte[1];

			@Override
			public void write(int foo) throws IOException {
				tmp[0] = (byte) foo;
				JHttpTunnelClient.this.write(tmp, 0, 1);
			}

			@Override
			public void write(byte[] foo) throws IOException {
				JHttpTunnelClient.this.write(foo, 0, foo.length);
			}

			@Override
			public void write(byte[] foo, int s, int l) throws IOException {
				JHttpTunnelClient.this.write(foo, s, l);
			}
		};
		return out;
	}

	public void close() {
		// System.out.println("close");
		try {
			sendClose();
		} catch (Exception e) {
		}

		try {
			ib.close();
		} catch (Exception e) {
		}

		try {
			ob.close();
		} catch (Exception e) {
		}
		closed = true;
	}

	public void setInBound(InBound ib) {
		this.ib = ib;
	}

	public void setOutBound(OutBound ob) {
		this.ob = ob;
	}

	public static void main(String[] args) {
		try {
			if (args.length == 0) {
				System.err.println("Enter hostname[:port]");
				System.exit(1);
			}
			String host = args[0];
			int hport = 8888;
			if (host.indexOf(':') != -1) {
				hport = Integer.parseInt(host.substring(host.lastIndexOf(':') + 1));
				host = host.substring(0, host.lastIndexOf(':'));
			}
			int lport = 2323;
			String _lport = System.getProperty("lport");
			if (_lport != null) {
				lport = Integer.parseInt(_lport);
			}
			String proxy_host = System.getProperty("proxy");
			int proxy_port = 8080;
			if (proxy_host != null && proxy_host.indexOf(':') != -1) {
				proxy_port = Integer.parseInt(proxy_host.substring(proxy_host.lastIndexOf(':') + 1));
				proxy_host = proxy_host.substring(0, proxy_host.lastIndexOf(':'));
			}
			
			System.out.println("Opening local port: " + lport);
			ServerSocket ss = new ServerSocket(lport);
			ss.setReuseAddress(true);
			Socket socket = ss.accept();
			socket.setTcpNoDelay(true);			

			final InputStream sin = socket.getInputStream();
			final OutputStream sout = socket.getOutputStream();

			final JHttpTunnelClient jhtc = new JHttpTunnelClient(host, hport);
			if (proxy_host != null) {
				jhtc.setProxy(proxy_host, proxy_port);
			}

			jhtc.setInBound(new InBoundSocket());
			jhtc.setOutBound(new OutBoundSocket());

			jhtc.connect();

			final InputStream jin = jhtc.getInputStream();
			final OutputStream jout = jhtc.getOutputStream();

			Runnable runnable = new Runnable() {
				public void run() {
					byte[] tmp = new byte[1024];
					try {
						while (true) {
							int i = jin.read(tmp);
							if (i > 0) {
								sout.write(tmp, 0, i);
								continue;
							}
							break;
						}
					} catch (Exception e) {
					}
					try {
						sin.close();
						jin.close();
						jhtc.close();
					} catch (Exception e) {
					}
				}
			};
			(new Thread(runnable)).start();
			
			byte[] tmp = new byte[1024];
			try {
				while (true) {
					int i = sin.read(tmp);
					System.out.println("i=" + i + " " + jout);
					if (i > 0) {
						jout.write(tmp, 0, i);
						continue;
					}
					break;
				}
			} catch (Exception e) {
			}
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			System.err.println("JHttpClient error: " + errors.toString());			
		}
	}
}
