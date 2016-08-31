package com.arichnet.jhttptunnel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Hashtable;
import org.apache.log4j.Logger;

public class InBoundServer extends BoundServer{
	private static final Logger log = Logger.getLogger(InBoundServer.class);
	private int traffic_tag;
	private Hashtable<Integer, ByteBuffer> server_inbound_table;
	private boolean table_lock;
	
	public InBoundServer(ForwardClient f){
		setForwardClient(f);
		server_inbound_table = new Hashtable<Integer, ByteBuffer>();
	}
	
	public InBoundServer(){
		traffic_tag = 0;
		table_lock = false;
		server_inbound_table = new Hashtable<Integer, ByteBuffer>();
	}
	
	public synchronized void writeTable(byte[] data){
		try {			
			ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
			buf.put(data);
			server_inbound_table.put(new Integer(traffic_tag), buf);
			log.debug("InBoundServer data wrote: " + data.length);
			traffic_tag++;
		}
		catch (Exception e){
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("InBoundServer Write Error: " + errors.toString());
		}
	}
	
	public synchronized byte[] readTable(int position){
		byte[] ret_value = null;
		if (server_inbound_table.isEmpty() || (position == 0))
			return ret_value;
		lockTable();
		try {
			// Get the lowest traffic tag
			Integer temp = new Integer(0);
			Integer pivot = Integer.MAX_VALUE;
			Enumeration keys = server_inbound_table.keys();
			
			while(keys.hasMoreElements()) {
				temp = (Integer) keys.nextElement();
				if (temp < pivot)
					pivot = temp;
			}
			
			log.debug("The lowest data tag found: " + pivot);
			
			ByteBuffer buf = (ByteBuffer) server_inbound_table.get(pivot);
			ret_value = new byte[position];
			int actual_position = buf.position();
			if (position < actual_position) {
				// We read until position and store the rest
				buf.rewind();
				buf.get(ret_value);
				// We get the rest of bytes to store on a new ByteBuffer
				byte[] tempbyte = new byte[actual_position - position];
				buf.get(tempbyte);
				ByteBuffer tempbuf = ByteBuffer.allocateDirect(tempbyte.length);
				tempbuf.put(tempbyte);
				// Remove the data from the table and add new one
				server_inbound_table.remove(pivot);
				server_inbound_table.put(pivot, tempbuf);
				log.debug("Data total in buffer: " + actual_position + " | " +
				          "Data read: " + position + " | " +
						  "Data remain: " + tempbyte.length);
			}
			else if (position == actual_position) {
				buf.rewind();
				buf.get(ret_value);				
				// Remove this Buffer from the InBound traffic table
				server_inbound_table.remove(pivot);
				log.debug("Data total in buffer: " + actual_position);
			}
			else {
				throw new Exception();
			}						
		}
		catch (Exception e){
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log.error("InBoundServer Write Error: " + errors.toString());
		}
		unlockTable();
		return ret_value;
	}
	
	public synchronized int nextBufferData() {
		int ret_value = 0;
		if (server_inbound_table.isEmpty())
			return ret_value;
		// Get the lowest traffic tag
		Integer temp = new Integer(0);
		Integer pivot = Integer.MAX_VALUE;
		Enumeration keys = server_inbound_table.keys();
		
		while(keys.hasMoreElements()) {
			temp = (Integer) keys.nextElement();
			if (temp < pivot)
				pivot = temp;
		}
		ByteBuffer buf = (ByteBuffer) server_inbound_table.get(pivot);
		return buf.position();
	}
	
	public synchronized boolean isLocked(){
		//log.debug("InBound is locked: " + table_lock);
		return table_lock;
	}
	
	public synchronized boolean hasData(){
		boolean ret_value = false;		
		if (!server_inbound_table.isEmpty())
			ret_value = true;
		//log.debug("InBound has data: " + ret_value);
		return ret_value;
	}
	
	public void lockTable(){
		//log.debug("InBound locking");
		table_lock = true;
	}
	
	public void unlockTable(){
		//log.debug("InBound unlocking");
		table_lock = false;
	}
}
