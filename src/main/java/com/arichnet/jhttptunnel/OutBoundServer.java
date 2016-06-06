package com.arichnet.jhttptunnel;

import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Hashtable;

public class OutBoundServer extends BoundServer{
	private Hashtable<Integer, ByteBuffer> buffer_table = new Hashtable<Integer, ByteBuffer>();	
	
	public OutBoundServer(ForwardClient f){
		setForwardClient(f);
	}
	
	public int writeData(byte[] data, int remote_p) {
		// Get the remote port from the table
		Integer remote_port = new Integer(remote_p);
		ByteBuffer buf = (ByteBuffer) buffer_table.get(new Integer(remote_port));
		int data_wrote;
		
		if (buf == null) 
			buf = ByteBuffer.allocateDirect(JHttpTunnel.BUFFER_LENGTH);
		
		if (buf.position() >= JHttpTunnel.BUFFER_LENGTH)
			return -1;
		
		if (buf.position() + data.length > JHttpTunnel.BUFFER_LENGTH) {
			data_wrote = JHttpTunnel.BUFFER_LENGTH - buf.position();
			buf.put(data, 0, data_wrote);
		}
		else {
			data_wrote = data.length;
			buf.put(data);
		}
		
		buffer_table.put(remote_port, buf);
		
		return data_wrote;
	}
	
	
	public byte[] read(){
		// Get key minimum remote port value
		Integer pivot = new Integer(65536);
		Integer temp;
		Enumeration remote_ports = buffer_table.keys();
		while(remote_ports.hasMoreElements()){
			temp = (Integer)remote_ports.nextElement();
			if (temp < pivot)
				pivot = temp;
		}
		ByteBuffer buffer = (ByteBuffer) buffer_table.get(pivot);
		int pos = buffer.position();
		byte[] return_value = new byte[pos];
		buffer.rewind();
		buffer.get(return_value);
		return return_value;
	}
}
