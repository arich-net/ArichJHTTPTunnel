

class JHTTPServerConnection
{
	private MySocket mySocket = null;
	private final String rootDirectory = ".";
	private final String defaultFile = "index.html";

	JHTTPServerConnection (Socket s) throws IOException	{
		super ();
		mySocket = new MySocket (s);
	}

	private Vector getHttpHeader (MySocket ms) throws IOException {
		Vector v = new Vector ();
		String foo = null;
		while (true) {
			foo = ms.readLine ();
			if (foo.length () == 0) {
				break;
			}
			v.addElement (foo);
		}
		return v;
	}
}
