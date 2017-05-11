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

import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.log4j.Logger;

import java.io.*;

public class OutBoundSocket extends OutBound {
	private static final Logger log = Logger.getLogger(OutBoundSocket.class);
	static final private byte[] _rn = "\r\n".getBytes();

	private Socket socket = null;
	private InputStream in = null;
	private OutputStream out = null;	

	@Override
	public void connect() throws IOException,
	 							 KeyStoreException,
	 							 CertificateException,
	 							 NoSuchAlgorithmException,
	 							 UnrecoverableKeyException,
	 							 KeyManagementException {
		//close(); This was causing the socket to cleanup before being processes on the server side
		log.info("Calling connect from: " + this.getClass().getName());

		String host = getHost();
		int port = getPort();
		int sid = getSid();
		boolean ssl = getSSL();
		String kspass = getKsPass();

		String request = "/index.html?crap=" + sid + " HTTP/1.1";

		Proxy p = getProxy();
		if (p == null) {
		   if (!ssl) {
			  log.debug("Starting client POST over PLAIN connection");
		      socket = new Socket(host, port);
		      request = "POST " + request;
		   } else {
 			   // First we initialise the Keystore on the JAR file 	
			   KeyStore keyStore=KeyStore.getInstance(KeyStore.getDefaultType());
			   InputStream keyStream=ClassLoader.getSystemResourceAsStream("security/jhttpserver.jks");
			   keyStore.load(keyStream, kspass.toCharArray());
			    
			   KeyStore trustKeyStore=KeyStore.getInstance(KeyStore.getDefaultType());
			   keyStream=ClassLoader.getSystemResourceAsStream("security/jhttpserver-truststore.jks");
			   trustKeyStore.load(keyStream, kspass.toCharArray());
			   
			   // KeyManagers decide which key material to use
			   KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			   kmf.init(keyStore, kspass.toCharArray());
			    
			   // TrustManagers decide whether to allow connections
			   TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			   tmf.init(trustKeyStore);
			    
			   SSLContext context = SSLContext.getInstance("TLS");
			   context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			   
			   log.debug("Starting client POST over TLS connection");
			   SSLSocketFactory ssf = context.getSocketFactory();
			   socket = ssf.createSocket(host, port);
			   request = "POST " + request;
			   
		   }
		} else {
			String phost = p.getHost();
			int pport = p.getPort();
			socket = new Socket(phost, pport);
			request = "POST http://" + host + ":" + port + request;
		}
		socket.setTcpNoDelay(true);

		in = socket.getInputStream();
		out = socket.getOutputStream();
		
		// Testing binary data
		//byte[] command = new byte[4];
		//command[0] = JHttpTunnel.TUNNEL_OPEN;
		//command[1] = 0;
		//command[2] = 1;
		//command[3] = 0;
		
		//out.write(command, 0, 4);
		
		out.write(request.getBytes());
		out.write(_rn);
		out.write(("Host: " + host + ":" + port).getBytes());
		out.write(_rn);
		out.write("Cache-Control: no-cache, no-store".getBytes());
		out.write(_rn);
		out.write(("Content-Length: " + getContentLength()).getBytes());
		out.write(_rn);
		out.write("Connection: close".getBytes());
		
		out.write(_rn);

		out.write(_rn);
		out.flush();
		log.debug("Finished starting POST Data with SSL=" + ssl + " Out=" + out);

		sendCount = getContentLength();
		// setOutputStream(out);
	}

	@Override
	public void sendData(byte[] foo, int s, int l, boolean flush) throws IOException,
	   																	 KeyManagementException,
	   																	 UnrecoverableKeyException,
	   																	 NoSuchAlgorithmException,
	   																	 CertificateException,
	   																	 KeyStoreException {
		log.debug("sendDtat: l="+l+" sendCount="+sendCount+" s="+s);
		
		if (l <= 0)
			return;
		if (sendCount <= 0) {
			log.debug("1#");			
			connect();
		}

		int retry = 2;
		while (retry > 0) {
			try {
				log.debug("Data to write " + Arrays.toString(Arrays.copyOf(foo, l)) + " OUT=" + out);
				out.write(foo, s, l);
				//out.write(foo);
				if (flush) {
					out.flush();
				}
				sendCount -= l;
				return;
			} catch (SocketException e) {
				System.out.println("SocketException 2# "+e+" "+l+" "+flush);
				throw e;
			} catch (IOException e) {
				System.out.println("IOException 21# "+e+" "+l+" "+flush);
				connect();
			}
			retry--;
		}
	}

	@Override
	public void close() throws IOException {
		if (socket != null) {
			if (out != null) {
				try {
					log.debug("Flushing and closing socket");					
					out.flush();
					out.close();
				} catch (IOException e) {
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
			socket.close();
			socket = null;
		}
	}
}
