package com.arichnet.jhttptunnel;

import java.util.*;
import java.nio.*;

abstract class BoundServer {	
	private ForwardClient forward_client;
	private ByteBuffer buffer = ByteBuffer.allocateDirect(JHttpTunnel.BUFFER_LENGTH);
	private boolean LockWrite;
	private boolean LockRead;
	
	public void setForwardClient(ForwardClient f) {
		forward_client = f;
	}
	
	public ForwardClient getForwardClient(ForwardClient f) {
		return forward_client;
	}
	
	public void setLockWrite(boolean lock) {
		LockWrite = lock;
	}
	
	public void setLockRead(boolean lock) {
		LockRead = lock;
	}
	
	public boolean getLockWrite() {
		return LockWrite;
	}
	
	public boolean getLockRead() {
		return LockRead;
	}
	
	public int writeBuffer(byte[] data) {
		int data_wrote = 0;
		if ((LockWrite) ||
			(buffer.position() == JHttpTunnel.BUFFER_LENGTH))
			return -1;		
		setLockWrite(true);
		if (buffer.position() + data.length > JHttpTunnel.BUFFER_LENGTH) {
			data_wrote = JHttpTunnel.BUFFER_LENGTH - buffer.position();
			buffer.put(data, 0, data_wrote);
		}
		else {
			data_wrote = data.length;
			buffer.put(data);
		}		
		setLockWrite(false);
		return data_wrote;
	}
	
	public byte[] readBuffer(){		
		if ((LockRead) ||
			(buffer.position() == 0))
			return null;		
		byte[] return_value = new byte[buffer.position()];
		setLockRead(true);
		buffer.get(return_value);
		setLockRead(false);
		return return_value;
	}
}
