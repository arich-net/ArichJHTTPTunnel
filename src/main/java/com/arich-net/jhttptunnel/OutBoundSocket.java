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

import java.net.*;
import java.io.*;

public class OutBoundSocket extends OutBound
{
	static final private byte[] _rn = "\r\n".getBytes ();

	private Socket socket = null;
	private InputStream in = null;
	private OutputStream out = null;

	@Override
	public void connect() throws IOException {
		close ();
		System.out.println("Calling connect from " + this.getClass().getName());

		String host = getHost ();
		int port = getPort ();
		int sid = getSid();

		String request = "/index.html?crap=" + sid + " HTTP/1.1";

		Proxy p = getProxy ();
		if (p == null) {
			socket = new Socket (host, port);
			request = "POST " + request;
		}
		else {
			String phost = p.getHost ();
			int pport = p.getPort ();
			socket = new Socket (phost, pport);
			request = "POST http://" + host + ":" + port + request;
		}
		socket.setTcpNoDelay (true);

		in = socket.getInputStream ();
		out = socket.getOutputStream ();
		out.write (request.getBytes());
		out.write (_rn);
		out.write (("Content-Length: " + getContentLength()).getBytes());
		out.write (_rn);
		out.write ("Connection: close".getBytes());
		out.write (_rn);
		out.write (("Host: " + host + ":" + port).getBytes());
		out.write (_rn);

		out.write (_rn);
		out.flush ();

		sendCount = getContentLength();
		// setOutputStream(out);
	}

	@Override
	public void sendData (byte[] foo, int s, int l, 
			              boolean flush) throws IOException {
		// System.out.println("sendDtat: l="+l+" sendCount="+sendCount);
		if (l <= 0) return;
		if (sendCount <= 0)	{
			System.out.println ("1#");
			connect ();
		}

		int retry = 2;
		while (retry > 0) {
			try
			{
				out.write (foo, s, l);
				if (flush)
				{
					out.flush ();
				}
				sendCount -= l;
				return;
			}
			catch (SocketException e)
			{
				// System.out.println("2# "+e+" "+l+" "+flush);
				throw e;
			}
			catch (IOException e)
			{
				// System.out.println("21# "+e+" "+l+" "+flush);
				connect ();
			}
			retry--;
		}
	}

	@Override
	public void close () throws IOException
	{
		if (socket != null)
		{
			if (out != null)
			{
				try
				{
					out.flush ();
					out.close ();
				}
				catch (IOException e)
				{
				}
			}
			if (in != null)
			{
				try
				{
					in.close ();
				}
				catch (IOException e)
				{
				}
			}
			socket.close ();
			socket = null;
		}
	}
}
