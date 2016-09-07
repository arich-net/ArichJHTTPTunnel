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
import java.util.concurrent.Callable;

public class JHttpServerHandler implements Callable {
	private static final Logger log = Logger.getLogger(JHttpServerHandler.class);
	private Socket socket;
	private String host;
	private int port;
	private Hashtable<String, ForwardClient> clientsTable;
	private List<Integer> postRemotePorts = new ArrayList<Integer>();
	private Hashtable<String, BoundServer> outBoundServerTable;
	private Hashtable<String, BoundServer> inBoundServerTable;
	private String result = "";
	
	public JHttpServerHandler(Socket s, String h, int p, Hashtable T, Hashtable o, Hashtable i){
		socket = s;
		host = h;
		port = p;
		clientsTable = T;
		outBoundServerTable = o;
		inBoundServerTable = i;
	}			
	
	public String call() {		
		try {
			result = (new JHttpServerConnection(socket, host, port, clientsTable, 
									   			outBoundServerTable, inBoundServerTable)).newsocket();
			log.info("Server connection closed!!!!!");

		} catch (Exception e) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("JHttpServer Error:" + errors.toString());
			result = "error";
		}

		return result;		
	}

}
