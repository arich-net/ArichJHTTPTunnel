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
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

public class JHttpTunnelServer extends Thread {
	private static final Logger log = Logger.getLogger(JHttpTunnelServer.class);

	static int connections = 0;
	static int client_connections = 0;
	static int source_connections = 0;

	private ServerSocket serverSocket = null;
	static int port = 8888;
	static String myaddress = null;
	static String myURL = null;
	static boolean ssl = false;

	private String forward_host;
	private int forward_port;
	//Initialise Forward Clients table linked to SESSIONID
	private Hashtable<String, ForwardClient> clientsTable;	
	//Initialise outBoundServerTable linked to SESSIONID
	private Hashtable<String, BoundServer> outBoundServerTable;
	//Initialise inBoundServerTable linked to SESSIONID
	private Hashtable<String, BoundServer> inBoundServerTable;
	
	private final ExecutorService pool;
	private ScheduledExecutorService cleaner_sched = null;
	private Future<String> thread_result;
	private Set<Future> future_results_set;

	JHttpTunnelServer(int port, int poolSize) {
		super();
		// We initialise the executor service
		pool = Executors.newFixedThreadPool(poolSize);
		connections = 0;
		try {
			serverSocket = new ServerSocket(port);
			myURL = (myaddress == null) ? 
					"http://" + InetAddress.getLocalHost().getHostAddress() + ":" + port :
					"http://" + myaddress + ":" + port;
			
			log.info("myURL: " + myURL);
			
		} catch (IOException e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("ServerSocket error: " + errors.toString());
			System.exit(1);
			
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("JHttpTunnelServer error: " + errors.toString());
		}
		
		// We initialise the cleaner service
		future_results_set = new HashSet<Future>();
		cleaner_sched = Executors.newScheduledThreadPool(2);
		startCleaner();
	}

	JHttpTunnelServer(int lport, String fhost, int fport, int poolSize) {		
		this(lport, poolSize);
		this.forward_host = fhost;
		this.forward_port = fport;
		// this.forward_client = new ForwardClient();
		this.clientsTable = new Hashtable<String, ForwardClient>();
		this.outBoundServerTable = new Hashtable<String, BoundServer>(); 
		this.inBoundServerTable = new Hashtable<String, BoundServer>();
	}

	@Override
	public void run() {
		Socket socket = null;
		try {
			while (true) {
			
				socket = serverSocket.accept();
				connections++;
				
				// new Spawn(socket);
				final Socket _socket = socket;
				final String _host = forward_host;
				final int _port = forward_port;
				// final ForwardClient _forwardclient = forward_client;
				final Hashtable<String, ForwardClient> _clientsTable = clientsTable;
				final Hashtable<String, BoundServer> _outBoundServerTable = outBoundServerTable;
				final Hashtable<String, BoundServer> _inBoundServerTable = inBoundServerTable;			
				
				thread_result = pool.submit(
						new JHttpServerHandler(_socket, _host, _port, _clientsTable, 
											   _outBoundServerTable, _inBoundServerTable)
				);
	
				future_results_set.add(thread_result);
				log.info("Thread for JHttpServerHandler started!. Adding " + thread_result + " to thread-result-set");
	
			}
		} catch (IOException e) {
			log.error("Socket accept error, probably the port is busy");
			System.exit(1);
		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("JHttpTunnelServer Exception: " + errors.toString());	
		}
	}

	public void startCleaner() {
		final Set<Future> ffuture_results_set = future_results_set;
		Runnable runnable_cleaner = new Runnable() {
			@Override
			public void run() {
				Set<Future> to_remove = new HashSet<Future>();
				log.debug("Running cleaner at specific interval time set");
				try {
					if (!ffuture_results_set.isEmpty()) {
						Iterator future_iter = ffuture_results_set.iterator();
	            		while (future_iter.hasNext()) {
	            			Future future = (Future) future_iter.next();
	            			if (future.isDone()) {
	            				log.debug("Result for " + future + " is " + future.get());
	            						to_remove.add(future);
	            			}
	            		}
					}
					// Checking the Future Objects to remove
					if (!to_remove.isEmpty()) {
						Iterator future_toremove_iter = to_remove.iterator();
						while (future_toremove_iter.hasNext()) {							
							Future temp = (Future) future_toremove_iter.next();
							ffuture_results_set.remove(temp);
							log.debug("Future object " + temp + " removed from Future list");
						}
					}

				} catch (InterruptedException e) {
					StringWriter errors = new StringWriter();
					e.printStackTrace(new PrintWriter(errors));
					log.error("Cleaner InterruptedException: " + errors.toString());

				} catch (ExecutionException e) {
					StringWriter errors = new StringWriter();
					e.printStackTrace(new PrintWriter(errors));
					log.error("Cleaner ExecutionException: " + errors.toString());
				}

				log.debug("Finishing cleaner");
			}
		};
		cleaner_sched.scheduleAtFixedRate(runnable_cleaner, 10, 30, TimeUnit.SECONDS);
    }

	public static void main(String[] args) {
		int port = 8888;
		if (args.length != 0) {
			String _port = args[0];
			if (_port != null) {
				port = Integer.parseInt(_port);
			}
		}

		String fhost = null;
		int fport = 0;
		int limitThreads = 10;
		String _fw = System.getProperty("forward");
		Boolean _ssl = new Boolean((String) System.getProperty("ssl"));
		String _kspass = System.getProperty("kspass");

		if (_fw != null && _fw.indexOf(':') != -1) {
			fport = Integer.parseInt(_fw.substring(_fw.lastIndexOf(':') + 1));
			fhost = _fw.substring(0, _fw.lastIndexOf(':'));
		}
		if (fport == 0 || fhost == null) {
			System.err.println("forward-port is not given");
			System.exit(1);
		}
		
	
		if (_ssl) {
			(new JHttpTunnelServerSSL(port, fhost, fport, limitThreads, _kspass)).start();
		}
		else {
			(new JHttpTunnelServer(port, fhost, fport, limitThreads)).start();
		}
	}
}
