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

package com.jcraft.jhttptunnel;

import java.util.*;
import java.io.*;

public abstract class Page
{
	static Hashtable map = new Hashtable ();

	static void register ()
	{
	}

	static void register (String src, Object dst)
	{
		synchronized (map)
		{
			map.put (src, dst);
		}
	}

	static Object map (String foo)
	{
		if (foo != null && foo.startsWith ("///"))
		{
			foo = foo.substring (2);
		}
		synchronized (map)
		{
			return map.get (foo);
		}
	}

	String decode (String arg)
	{

		byte[] foo = arg.getBytes ();
		StringBuffer sb = new StringBuffer ();
		for (int i = 0; i < foo.length; i++)
		{
			if (foo[i] == '+')
			{
				sb.append (' ');
				continue;
			}
			if (foo[i] == '%' && i + 2 < foo.length)
			{
				int bar = foo[i + 1];
				bar = ('0' <= bar && bar <= '9') ? bar - '0'
						: ('a' <= bar && bar <= 'z') ? bar - 'a' + 10
								: ('A' <= bar && bar <= 'Z') ? bar - 'A' + 10
										: bar;
				bar *= 16;
				int goo = foo[i + 2];
				goo = ('0' <= goo && goo <= '9') ? goo - '0'
						: ('a' <= goo && goo <= 'f') ? goo - 'a' + 10
								: ('A' <= goo && goo <= 'F') ? goo - 'A' + 10
										: goo;
				bar += goo;
				bar &= 0xff;
				sb.append ((char) bar);
				i += 2;
				continue;
			}
			sb.append ((char) foo[i]);
		}
		return sb.toString ();
	}

	static void forward (MySocket mysocket, String location) throws IOException
	{
		mysocket.println ("HTTP/1.0 302 Found");
		// mysocket.println("Location: "+HttpServer.myURL+location);
		mysocket.println ("Location: " + location);
		mysocket.println ("");
		mysocket.flush ();
		mysocket.close ();
	}

	static void unknown (MySocket mysocket, String location) throws IOException
	{
		mysocket.println ("HTTP/1.0 404 Not Found");
		mysocket.println ("Connection: close");
		mysocket.println ("Content-Type: text/html; charset=iso-8859-1");
		mysocket.println ("");
		mysocket.println ("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
		mysocket.println ("<HTML><HEAD><TITLE>404 Not Found</TITLE></HEAD><BODY>");
		mysocket.println ("<H1>Not Found</H1>");
		mysocket.println ("The requested URL " + location
				+ " was not found on this server.<P>");
		mysocket.println ("<HR>");
		mysocket.println ("<ADDRESS>JRoar at " + JHttpTunnelServer.myURL
				+ "/</ADDRESS>");
		mysocket.println ("</BODY></HTML>");
		mysocket.flush ();
		mysocket.close ();
	}

	static void ok (MySocket mysocket, String location) throws IOException
	{
		mysocket.println ("HTTP/1.0 200 OK");
		mysocket.println ("Last-Modified: Thu, 04 Oct 2001 14:09:23 GMT");
		mysocket.println ("Connection: close");
		// mysocket.println("Content-Type: text/html; charset=iso-8859-1");
		mysocket.println ("");
		mysocket.flush ();
		mysocket.close ();
	}

	abstract void kick (MySocket mysocket, Hashtable ht, Vector v)
			throws IOException;

	Hashtable getVars (String arg)
	{

		Hashtable vars = new Hashtable ();
		vars.put ("jroar-method", "GET");
		if (arg == null) return vars;

		arg = decode (arg);

		int foo = 0;
		int i = 0;
		int c = 0;

		String key, value;

		while (true)
		{
			key = value = null;

			foo = arg.indexOf ('=');
			if (foo == -1) break;
			key = arg.substring (0, foo);
			arg = arg.substring (foo + 1);

			foo = arg.indexOf ('&');
			if (foo != -1)
			{
				value = arg.substring (0, foo);
				arg = arg.substring (foo + 1);
			}
			else
				value = arg;

			vars.put (key, value);

			if (foo == -1) break;
		}
		return vars;
	}

	Hashtable getVars (MySocket mysocket, int len)
	{
		Hashtable vars = new Hashtable ();
		vars.put ("jroar-method", "POST");
		if (len == 0) return vars;

		int i = 0;
		int c = 0;
		StringBuffer sb = new StringBuffer ();
		String key, value;

		while (i < len)
		{
			key = value = null;
			sb.setLength (0);
			while (i < len)
			{
				c = mysocket.readByte ();
				i++;
				if (c == '=')
				{
					key = sb.toString ();
					break;
				}
				sb.append ((char) c);
			}
			sb.setLength (0);
			while (i < len)
			{
				c = mysocket.readByte ();
				i++;
				if (c == '&')
				{
					value = sb.toString ();
					break;
				}
				sb.append ((char) c);
			}
			if (key != null && value != null)
			{
				key = decode (key);
				value = decode (value);
				vars.put (key, value);
			}
		}
		return vars;
	}

	static void notFound (MySocket ms) throws IOException
	{
		ms.println ("HTTP/1.0 404 Not Found");
		ms.println ("Content-Type: text/html");
		ms.println ("");
		ms.println ("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
		ms.println ("<HTML><HEAD><TITLE>404 Not Found</TITLE></HEAD><BODY>");
		ms.println ("<H1>Not Found</H1>The requested URL was not found on this server.<HR>");
		ms.println ("</BODY></HTML>");
		ms.flush ();
		ms.close ();
	}
}
