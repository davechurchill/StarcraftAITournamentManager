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
	
	public String toString()
	{
		String p = "Client properties:";
		for (int i = 0; i < properties.size(); i++)
		{
			p += " " + properties.get(i);
			if (i != properties.size() - 1)
			{
				p += ",";
			}
		}
		return p;
	}

}
