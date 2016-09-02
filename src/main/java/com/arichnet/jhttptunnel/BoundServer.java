package com.arichnet.jhttptunnel;

import java.util.*;
import java.nio.*;

abstract class BoundServer {	
	private ForwardClient forward_client;
	private ByteBuffer buffer = ByteBuffer.allocateDirect(JHttpTunnel.BUFFER_LENGTH);
	private boolean LockWrite;
	private boolean LockRead;
	private boolean tunnelOpened = false;
	private boolean boundLocked = false;
	private boolean sendClose = false;
	
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
	
	public boolean getTunnelOpened() {
		return tunnelOpened;
	}
	
	public void setTunnelOpened(boolean flag) {
		tunnelOpened = flag;		
	}
	
	public void fcl_message() {
		forward_client.message();
	}
	
	public void fcl_close() {
		forward_client.close();
	}
	
	public boolean fcl_isClosed() {
		return forward_client.isClosed();
	}	

	public void setBoundLocked(boolean flag) {
		boundLocked = flag;
	}

	public boolean getBoundLocked() {
		return boundLocked;
	}

	public void setSendClose(boolean flag) {
		sendClose = flag;
	}

	public boolean getSendClose() {
		return sendClose;
	}

}
