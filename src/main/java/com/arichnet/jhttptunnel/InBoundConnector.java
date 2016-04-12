/* -*-mode:java; c-basic-offset:2; -*- */
/*
 Copyright (c) 2005 ymnk, JCraft,Inc. All rights reserved.

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

public class InBoundConnector extends InBound {
	private InputStream in = null;
	private HttpURLConnection con = null;

	@Override
	public void connect() throws IOException {
		close();
		System.out.println("Calling connect from " + this.getClass().getName());
		String host = getHost();
		int port = getPort();
		URL url = new URL("http://" + host + ":" + port + "/index.html?crap=1");
		con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		in = con.getInputStream();
	}

	@Override
	public int receiveData(byte[] buf, int s, int l) throws IOException {
		// System.out.println("receiveData: "+l);
		if (l <= 0) {
			return -1;
		}
		while (true) {
			// if(closed) return -1;
			try {
				if (buf == null) {
					if (l <= 0)
						return -1;
					long bar = in.skip(l);
					l -= bar;
					continue;
				}
				int i = in.read(buf, s, l);
				if (i > 0) {
					return i;
				}
				connect();
			}
			// catch(SocketException e){
			// throw e;
			// }
			catch (IOException e) {
				// System.out.println("2$ "+e);
				throw e;
				// connect();
			}
		}
	}

	@Override
	public void close() {
		// System.out.println("InBound.close: ");
		if (con != null) {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
			con.disconnect();
			con = null;
		}
	}
}
