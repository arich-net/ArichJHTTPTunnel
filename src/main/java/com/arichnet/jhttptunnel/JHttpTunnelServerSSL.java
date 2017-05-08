package com.arichnet.jhttptunnel;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Certificate;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;

import org.apache.log4j.Logger;

//import com.sun.net.ssl.KeyManagerFactory;
//import com.sun.net.ssl.SSLContext;

public class JHttpTunnelServerSSL extends Thread  {
	
	private static final Logger log = Logger.getLogger(JHttpTunnelServerSSL.class);
	
	private SSLServerSocket serverSocket = null;
	static int port = 8889;
	static String myaddress = null;
	static String myURL = null;
	static int connections = 0;
	
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
	
	private String[] tlsProtocols =  { "TLSv1.2", "SSLv3" };
	private String[] tlsCipherSuites = { "SSL_RSA_WITH_3DES_EDE_CBC_SHA", 
			 							 "TLS_RSA_WITH_AES_128_CBC_SHA",
			 							 "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
			 							 "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
			 							 "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" };
	private boolean tlsNeedClientAuth = false;
	
	
	
	JHttpTunnelServerSSL(int port, int poolSize, String kspass) {
		super();
		pool = Executors.newFixedThreadPool(poolSize);
		connections = 0;
		try {
			// First we initialise the keystores on the JAR file 	
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
		    
			log.debug("Starting JHttpTunnel with TLS");
			SSLServerSocketFactory ssf = context.getServerSocketFactory();
			
			//SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
		    serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
		    serverSocket.setEnabledProtocols(tlsProtocols);
		    serverSocket.setEnabledCipherSuites(tlsCipherSuites);
		    serverSocket.setNeedClientAuth(tlsNeedClientAuth);
		    
		    log.debug("Cipher Suites Supported" + Arrays.toString(tlsCipherSuites));
		    
		    myURL = (myaddress == null) ? 
					"https://" + InetAddress.getLocalHost().getHostAddress() + ":" + port :
					"https://" + myaddress + ":" + port;
			
			log.info("myURL: " + myURL);
			
			// We initialise the cleaner service
			future_results_set = new HashSet<Future>();
			cleaner_sched = Executors.newScheduledThreadPool(2);
			startCleaner();			
		    
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
	}
	
	JHttpTunnelServerSSL(int lport, String fhost, int fport, int poolSize, String kspass) {		
		this(lport, poolSize, kspass);
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
		while (true) {
			try {
				log.debug("Accepting SSL socket");
				socket = serverSocket.accept();
				SSLSession session = ((SSLSocket) socket).getSession();
			    log.debug("Peer host is " + session.getPeerHost());
			    log.debug("Cipher is " + session.getCipherSuite());
			    log.debug("Protocol is " + session.getProtocol());
			    log.debug("ID is " + new BigInteger(session.getId()));
			    log.debug("Session created in " + session.getCreationTime());
			    log.debug("Session accessed in " + session.getLastAccessedTime());
			    
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
			
			} catch (IOException e) {
				log.error("Socket accept error, probably the port is busy");
				System.exit(1);
				
			} catch (Exception e) {
				StringWriter errors = new StringWriter();
				e.printStackTrace(new PrintWriter(errors));
				log.error("JHttpTunnelServer Exception: " + errors.toString());	
			}
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
	
}
