package com.arichnet.jhttptunnel;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;
import org.apache.log4j.Logger;

public class OutBoundServer extends BoundServer{
	private static final Logger log = Logger.getLogger(OutBoundServer.class);
	private Hashtable<Integer, ByteBuffer> buffer_table = new Hashtable<Integer, ByteBuffer>();
	
	public OutBoundServer(ForwardClient f){
		setForwardClient(f);
	}
	
	public OutBoundServer(){
	}
	
	public synchronized int writeData(byte[] data, int remote_p) {
		// Get the remote port from the table
		Integer remote_port = new Integer(remote_p);
		ByteBuffer buf = (ByteBuffer) buffer_table.get(new Integer(remote_port));
		int data_wrote;				
		
		if ((buf == null) || (buf.position() >= JHttpTunnel.BUFFER_LENGTH)) {
			log.debug("Buffer OutBoundServer NULL or Exceeded maximum value");
			return -1;
		}
		
		if (buf.position() + data.length > JHttpTunnel.BUFFER_LENGTH) {
			data_wrote = JHttpTunnel.BUFFER_LENGTH - buf.position();
			buf.put(data, 0, data_wrote);
		}
		else {
			data_wrote = data.length;
			buf.put(data);
		}		
		buffer_table.remove(remote_port);
		buffer_table.put(remote_port, buf);
		log.debug("Buffer Data Stored:" + buf.position() + " | TCP remote port:" + remote_p);
		
		return data_wrote;
	}		
	
	public synchronized byte[] readData(){
		// Get key minimum remote port value
		if (buffer_table.isEmpty())
			return null;
		Integer pivot = new Integer(65536);
		Integer temp;
		Enumeration remote_ports = buffer_table.keys();		
		while(remote_ports.hasMoreElements()){
			temp = (Integer)remote_ports.nextElement();
			if (temp < pivot)
				pivot = temp;
		}
		ByteBuffer buffer = (ByteBuffer) buffer_table.get(pivot);
		if ((buffer == null) || (buffer.position() == 0))
			return null;
		int pos = buffer.position();
		byte[] return_value = new byte[pos];
		buffer.rewind();
		buffer.get(return_value);
		buffer.rewind();
		//buffer_table.remove(pivot);
		return return_value;
	}
	
	public synchronized boolean removePort(int remote_p) {
		try {
		   buffer_table.remove(new Integer(remote_p));
		   log.debug("Close remote port:" + remote_p);
		   return true;
		}
		catch (Exception e){
			return false;
		}
	}
	
	public synchronized boolean initPort(int remote_p) {
		try {
		   ByteBuffer buf = ByteBuffer.allocateDirect(JHttpTunnel.BUFFER_LENGTH);
		   buffer_table.put(new Integer(remote_p), buf);
		   log.debug("Init remote port:" + remote_p);
		   return true;
		}
		catch (Exception e){
			return false;
		}
	}
	
	public synchronized int getBufferPosition(int remote_p){
		try {
			Integer remote_port = new Integer(remote_p);
			ByteBuffer buf = (ByteBuffer) buffer_table.get(new Integer(remote_port));
			return buf.position();
		}
		catch (Exception e) {
			return -1;
		}
	}
	
	@Override
	public String toString(){
		StringBuilder ret_value = new StringBuilder();
		Integer key;
		ByteBuffer buf;
		Enumeration remote_ports = buffer_table.keys();
		while(remote_ports.hasMoreElements()){
			key = (Integer)remote_ports.nextElement();
			buf = (ByteBuffer) buffer_table.get(key);		
			ret_value.append(" (" + key.toString() + "=>" + String.valueOf(buf.position()) + ")");			
		}
		return ret_value.toString();
	}
}
