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

package com.jcraft.jhttptunnel;

import java.io.*;
import java.net.*;
import java.util.*;

public class JHttpTunnelServer extends Thread
{
	static int connections = 0;
	static int client_connections = 0;
	static int source_connections = 0;

	private ServerSocket serverSocket = null;
	static int port = 8888;
	static String myaddress = null;
	static String myURL = null;

	static String default_host;
	static int default_port;

	public JHttpTunnelServer (int port)
	{
		super ();
		connections = 0;
		try
		{
			serverSocket = new ServerSocket (port);
		}
		catch (IOException e)
		{
			// System.out.println("ServerSocket error"+e );
			System.exit (1);
		}
		try
		{
			if (myaddress == null)
				myURL = "http://"
						+ InetAddress.getLocalHost ().getHostAddress () + ":"
						+ port;
			else
				myURL = "http://" + myaddress + ":" + port;
			// System.out.println("myURL: "+myURL);
		}
		catch (Exception e)
		{
			System.out.println (e);
		}
	}

	public JHttpTunnelServer (int lport, String fhost, int fport)
	{
		this (lport);
		this.default_host = fhost;
		this.default_port = fport;
	}

	@Override
	public void run ()
	{
		Socket socket = null;
		while (true)
		{
			try
			{
				socket = serverSocket.accept ();
			}
			catch (IOException e)
			{
				System.out.println ("accept error");
				System.exit (1);
			}
			connections++;
			// new Spawn(socket);
			final Socket _socket = socket;
			new Thread (new Runnable ()
			{
				@Override
				public void run ()
				{
					try
					{
						System.out.println("Thread: " + Thread.currentThread().getName() + " |Running before doit");
						(new Dispatch (_socket)).doit ();
					}
					catch (Exception e)
					{
					}
				}
			}).start ();
		}
	}

	/* class Spawn extends Thread{ private Socket socket=null; Spawn(Socket
	 * socket){ super(); this.socket=socket; start(); } public void run(){
	 * try{(new Dispatch(socket)).doit();} catch(Exception e){} } } */

	public static void main (String[] arg)
	{

		int port = 8888;
		if (arg.length != 0)
		{
			String _port = arg[0];
			if (_port != null)
			{
				port = Integer.parseInt (_port);
			}
		}

		String fhost = null;
		int fport = 0;
		String _fw = System.getProperty("forward");
		System.out.println("Thread: " + Thread.currentThread().getName() + " | Forward Value: " + _fw);
		
		if (_fw != null && _fw.indexOf (':') != -1)
		{
			fport = Integer
					.parseInt (_fw.substring (_fw.lastIndexOf (':') + 1));
			fhost = _fw.substring (0, _fw.lastIndexOf (':'));
		}
		if (fport == 0 || fhost == null)
		{
			System.err.println ("forward-port is not given");
			System.exit (1);
		}
		(new JHttpTunnelServer (port, fhost, fport)).start ();
	}
}

class Dispatch
{
	private MySocket mySocket = null;
	private final String rootDirectory = ".";
	private final String defaultFile = "index.html";
	private static int CONTENT_LENGTH = 0;

	Dispatch (Socket s) throws IOException
	{
		super ();
		mySocket = new MySocket (s);
	}

	private Vector getHttpHeader (MySocket ms) throws IOException
	{
		Vector v = new Vector ();
		String foo = null;
		while (true)
		{
			foo = ms.readLine ();
			System.out.println("Thread: " + Thread.currentThread().getName() + " |Reading inside Vector: " + foo);
			if (foo.length () == 0)
			{
				break;
			}
			v.addElement (foo);
		}
		return v;
	}

	byte[] buf = new byte[1024];

	private void procPOST (String socket_readline, Vector httpheader) throws IOException
	{
		System.out.println("Thread: " + Thread.currentThread().getName() + " | Processing procPOST: " + socket_readline);
		String foo;
		int len = 0;
		int c;
		String file = socket_readline.substring (socket_readline.indexOf (' ') + 1);
		if (file.indexOf (' ') != -1)
			file = file.substring (0, file.indexOf (' '));

		Hashtable vars = getVars((file.indexOf ('?') != -1) ? file.substring (file.indexOf ('?') + 1) : null);
		
		String sid = (String) vars.get ("SESSIONID");

		Client client = null;
		if (sid == null)
		{
			sid = new Long (System.currentTimeMillis ()).toString ();
			System.out.println("Thread: " + Thread.currentThread().getName() + " | Creating client with sid: " + sid);
			client = new Client (sid, JHttpTunnelServer.default_host,
					JHttpTunnelServer.default_port);
		}
		else
		{
			System.out.println("Thread: " + Thread.currentThread().getName() + " | Getting client with sid: " + sid);
			client = getClient (sid);
		}

		System.out.println("Thread: " + Thread.currentThread().getName() + " |client: " + client);

		if (client == null)
		{
			notFound (mySocket);
			return;
		}

		for (int i = 0; i < httpheader.size (); i++)
		{
			foo = (String) httpheader.elementAt (i);
			if (foo.startsWith ("Content-Length:")
					|| foo.startsWith ("Content-length:") // hmm... for Opera,
															// lynx
			)
			{
				foo = foo.substring (foo.indexOf (' ') + 1);
				foo = foo.trim ();
				len = Integer.parseInt (foo);
				Dispatch.CONTENT_LENGTH = len;
				System.out.println("Thread: " + Thread.currentThread().getName() + " | POST Content Length: " + len);
			}
		}

		// System.out.println("len: "+len);

		System.out.println("Thread: " + Thread.currentThread().getName() + " | Client command: " + 
				           client.command + " | Client Dataremain: " + client.dataremain);
		if (len == 0)
		{ // just read data
			client.command = -1;
			client.dataremain = 0;
		}

		if (client.dataremain == 0 && len > 0)
		{
			int i = mySocket.read (buf, 0, 1); // command
			len--;
			client.command = buf[0];
			int datalen = 0;

			if ((client.command & JHttpTunnel.TUNNEL_SIMPLE) == 0)
			{
				i = mySocket.read (buf, 0, 2);
				len -= 2;
				if (i != 2)
				{
				}
				datalen = (((buf[0]) << 8) & 0xff00);
				datalen = datalen | (buf[1] & 0xff);
			}
			// System.out.println("command: "+client.command+" "+datalen);
			client.dataremain = datalen;
		}

		// System.out.println("dataremain: "+client.dataremain+" len="+len);

		int i = 0;
		if (len > 0)
		{
			i = mySocket.read (buf, 0, len);
			// System.out.println("["+new String(buf, 0, i)+"]");
			client.dataremain -= len;
		}

		if (client.dataremain == 0)
		{
			System.out.println (sid + ": " + client.command);
			switch (client.command)
			{
			case JHttpTunnel.TUNNEL_OPEN:
				client.connect ();
				break;
			case JHttpTunnel.TUNNEL_DATA:
				client.send (buf, 0, len);
				break;
			case JHttpTunnel.TUNNEL_CLOSE:
				client.close ();
				break;
			}
		}

		i = 0;
		if (client.isConnected ())
		{
			i = client.pop (buf, 3, buf.length - 3);
			if (i > 0)
			{
				buf[0] = JHttpTunnel.TUNNEL_DATA;
				buf[1] = (byte) ((i >>> 8) & 0xff);
				buf[2] = (byte) (i & 0xff);
				i += 3;
			}
		}
		ok (mySocket, buf, 0, i, sid);
	}

	private void procGET (String string, Vector httpheader) throws IOException
	{
		String foo;
		int c;
		String file = string.substring (string.indexOf (' ') + 1);
		System.out.println("Thread: " + Thread.currentThread().getName() + " | Inside procGET file: " + file);
		
		if (file.indexOf (' ') != -1)
			file = file.substring (0, file.indexOf (' '));

		if (file.indexOf ("..") != -1)
		{
			notFound (mySocket);
			return;
		}

		if (file.startsWith ("/")) file = file.substring (1);

		try
		{
			file = "/tmp/" + file;
			File _file = new File (file);	
			if (! _file.exists()) _file.createNewFile();
			int len = (int) _file.length ();
			/**
			 * Trying to fix the continuous loop with GET because of the Content-Length
			 */
			System.out.println("Thread: " + Thread.currentThread().getName() + " | Getting Content Lenght from static content: " + Dispatch.CONTENT_LENGTH);
			//len = Dispatch.CONTENT_LENGTH;
			len = 102400;
			/**
			 * 
			 */
			System.out.println("Thread: " + Thread.currentThread().getName() + " | Trying to create file: " + file + " |length: " + len);
			FileInputStream fis = new FileInputStream (_file);
			ok (mySocket, fis, len, null);
			fis.close ();
		}
		catch (IOException e)
		{
			System.out.println ("Thread: " + Thread.currentThread().getName() + " | Got IOException: " + e.getMessage());
			e.printStackTrace();
		}

	}

	private void procHEAD (String string, Vector httpheader) throws IOException
	{
		ok (mySocket, null, 0, 0, "");
	}

	private String decode (String arg)
	{
		System.out.println("Thread: " + Thread.currentThread().getName() + " |Decoding: " + arg);
		byte[] foo = arg.getBytes ();
		StringBuffer sb = new StringBuffer ();
		for (int i = 0; i < foo.length; i++)
		{
			if (foo[i] == '+')
			{
				sb.append (' ');
				continue;
			}
			if (foo[i] == '%' && i + 2 < foo.length)
			{
				int bar = foo[i + 1];
				bar = ('0' <= bar && bar <= '9') ? bar - '0'
						: ('a' <= bar && bar <= 'z') ? bar - 'a' + 10
								: ('A' <= bar && bar <= 'Z') ? bar - 'A' + 10
										: bar;
				bar *= 16;
				int goo = foo[i + 2];
				goo = ('0' <= goo && goo <= '9') ? goo - '0'
						: ('a' <= goo && goo <= 'f') ? goo - 'a' + 10
								: ('A' <= goo && goo <= 'F') ? goo - 'A' + 10
										: goo;
				bar += goo;
				bar &= 0xff;
				sb.append ((char) bar);
				i += 2;
				continue;
			}
			sb.append ((char) foo[i]);
		}
		System.out.println("Thread: " + Thread.currentThread().getName() + " |Result: " + sb.toString());
		return sb.toString ();
	}

	private Hashtable getVars (String arg)
	{
		Hashtable vars = new Hashtable();
		if (arg == null) return vars;
		arg = decode (arg);
		int foo = 0;
		int i = 0;
		int c = 0;
		String key, value;
		while (true)
		{
			key = value = null;
			foo = arg.indexOf ('=');
			if (foo == -1) break;
			key = arg.substring (0, foo);
			arg = arg.substring (foo + 1);
			foo = arg.indexOf ('&');
			if (foo != -1)
			{
				value = arg.substring (0, foo);
				arg = arg.substring (foo + 1);
			}
			else
				value = arg;
			vars.put (key, value);
			if (foo == -1) break;
		}
		return vars;
	}

	public void doit ()
	{
		try
		{
			String socket_readline = mySocket.readLine ();

			System.out.println ("Thread: " + Thread.currentThread().getName() + " | " + mySocket.socket.getInetAddress () + ": " + socket_readline
					+ " " + (new java.util.Date ()));

			if (socket_readline.indexOf (' ') == -1)
			{
				mySocket.close ();
				return;
			}

			String http_method = socket_readline.substring (0, socket_readline.indexOf (' '));
			// System.out.println(socket_readline);

			Vector v = getHttpHeader (mySocket);

			// System.out.println(v);

			if (http_method.equalsIgnoreCase ("POST"))
			{
				procPOST (socket_readline, v);
				return;
			}

			if (http_method.equalsIgnoreCase ("GET"))
			{
				procGET (socket_readline, v);
				return;
			}

			if (http_method.equalsIgnoreCase ("HEAD"))
			{
				procHEAD (socket_readline, v);
				return;
			}
		}
		catch (Exception e)
		{
		}
	}

	void ok (MySocket mysocket, byte[] buf, int s, int l, String sid)
			throws IOException
	{
		mysocket.println ("HTTP/1.1 200 OK");
		mysocket.println ("Last-Modified: Thu, 04 Oct 2001 14:09:23 GMT");
		if (sid != null)
		{
			mysocket.println ("x-SESSIONID: " + sid);
		}
		mysocket.println ("Content-Length: " + l);
		mysocket.println ("Connection: close");
		mysocket.println ("Content-Type: text/html; charset=iso-8859-1");
		mysocket.println ("");

		if (l > 0)
		{
			mysocket.write (buf, s, l);
		}

		mysocket.flush ();
		mysocket.close ();
	}

	void ok (MySocket mysocket, InputStream in, int l, String sid)
			throws IOException
	{
		mysocket.println ("HTTP/1.1 200 OK");
		mysocket.println ("Last-Modified: Thu, 04 Oct 2001 14:09:23 GMT");
		if (sid != null)
		{
			mysocket.println ("x-SESSIONID: " + sid);
		}
		mysocket.println ("Content-Length: " + l);
		mysocket.println ("Connection: close");
		mysocket.println ("Content-Type: text/html; charset=iso-8859-1");
		mysocket.println ("");

		if (l > 0)
		{
			byte[] buf = new byte[1024];
			while (true)
			{
				int i = in.read (buf, 0, buf.length);
				if (i < 0) break;
				if (i > 0)
				{
					mysocket.write (buf, 0, i);
				}
			}
		}
		mysocket.flush ();
		mysocket.close ();
	}

	static void notFound (MySocket ms) throws IOException
	{
		ms.println ("HTTP/1.1 404 Not Found");
		ms.println ("Content-Type: text/html");
		ms.println ("Content-Length: 0");
		ms.println ("Connection: close");
		ms.println ("");
		ms.flush ();
		ms.close ();
	}

	private static Hashtable cpool = new Hashtable ();

	static Client getClient (String sid)
	{
		return (Client) cpool.get (sid);
	}

	static void putClient (String sid, Client client)
	{
		cpool.put (sid, client);
	}

	static void removeClient (String sid)
	{
		cpool.remove (sid);
	}

	class Client extends Thread
	{

		private final String sid;
		private final String host;
		private final int port;

		Client (String sid, String host, int port)
		{
			super ();
			this.sid = sid;
			this.host = host;
			this.port = port;
			putClient (sid, this);
		}

		public int dataremain = 0;
		public byte command = 0;

		private Socket socket = null;
		private InputStream in;
		private OutputStream out;

		boolean connected = false;

		boolean isConnected ()
		{
			return connected;
		}

		void connect ()
		{
			try
			{
				socket = new Socket (host, port);
				// System.out.println("socket: "+socket);
				in = socket.getInputStream ();
				out = socket.getOutputStream ();
				connected = true;
				start ();
			}
			catch (Exception e)
			{
				System.out.println (e);
			}
		}

		public void send (byte[] foo, int s, int l)
		{
			// System.out.println("send: "+new String(foo, s, l));
			try
			{
				out.write (foo, s, l);
				out.flush ();
			}
			catch (Exception e)
			{
				System.out.println (e);
			}
		}

		@Override
		public void run ()
		{
			byte[] buf = new byte[1024];
			while (true)
			{
				try
				{
					int space = space ();
					if (space > 0)
					{
						if (space > buf.length) space = buf.length;
						int i = in.read (buf, 0, space);
						System.out.println("Thread: " + Thread.currentThread().getName() + " | run read: "+i);
						if (i < 0)
						{
							break;
						}
						System.out.println("Thread: " + Thread.currentThread().getName() + " | " + new String(buf, 0, i));
						if (i > 0)
						{
							push (buf, 0, i);
							try
							{
								Thread.sleep (1);
							}
							catch (Exception ee)
							{
							}
							continue;
						}
					}
					while (true)
					{
						if (space () > 0) break;
						try
						{
							Thread.sleep (1000);
						}
						catch (Exception ee)
						{
						}
					}
				}
				catch (java.net.SocketException e)
				{
					// System.out.println(e);
					break;
				}
				catch (Exception e)
				{
					// System.out.println(e);
				}
			}
			close ();
		}

		int buflen = 0;
		// byte[] buf=new byte[1024];
		// byte[] buf=new byte[4096];
		byte[] buf = new byte[10240];

		synchronized int space ()
		{
			// System.out.println("space "+(buf.length-buflen));
			return buf.length - buflen;
		}

		synchronized void push (byte[] foo, int s, int l)
		{
			// System.out.println("push "+l);
			System.arraycopy (foo, s, buf, buflen, l);
			buflen += l;
		}

		synchronized int pop (byte[] foo, int s, int l)
		{
			if (buflen == 0)
			{
				// System.out.println("pop "+0);
				return 0;
			}
			if (l > buflen) l = buflen;
			System.arraycopy (buf, 0, foo, s, l);
			System.arraycopy (buf, l, buf, 0, buflen - l);
			buflen -= l;

			if (socket == null && buflen <= 0)
			{
				removeClient (sid);
			}
			// System.out.println("pop: "+l);
			return l;
		}

		public void close ()
		{
			try
			{
				in.close ();
				out.close ();
				socket.close ();
				socket = null;
			}
			catch (Exception e)
			{
			}
			if (buflen == 0)
			{
				removeClient (sid);
			}
		}

	}
}
