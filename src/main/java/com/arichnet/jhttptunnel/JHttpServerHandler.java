package com.arichnet.jhttptunnel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;
import org.apache.log4j.Logger;

public class JHttpServerHandler implements Runnable {
	private static final Logger log = Logger.getLogger(JHttpServerHandler.class);
	private Socket socket;
	private String host;
	private int port;
	private Hashtable<String, ForwardClient> clientsTable;
	private List<Integer> postRemotePorts = new ArrayList<Integer>();
	private Hashtable<String, BoundServer> outBoundServerTable;
	private Hashtable<String, BoundServer> inBoundServerTable;
	
	public JHttpServerHandler(Socket s, String h, int p, Hashtable T, List l, Hashtable o, Hashtable i){
		socket = s;
		host = h;
		port = p;
		clientsTable = T;
		postRemotePorts = l;
		outBoundServerTable = o;
		inBoundServerTable = i;
	}			
	
	public void run() {
		try {
			(new JHttpServerConnection(socket, host, port, clientsTable, 
					postRemotePorts, outBoundServerTable, inBoundServerTable)).newsocket();
			log.info("Server connection closed!!!!!");

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("JHttpServer Error:" + errors.toString());
		}		
		
	}

}
