package objects;

import java.util.Vector;

public class ClientPropertyMessage implements Message
{
	private static final long serialVersionUID = 7125961928769121190L;
	
	public Vector<String> properties;
	
	public ClientPropertyMessage(Vector<String> properties)
	{
		this.properties = properties;
	}

}
