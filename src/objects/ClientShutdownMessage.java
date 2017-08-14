package objects;

public class ClientShutdownMessage implements Message
{
	private static final long serialVersionUID = -4355952694463888484L;

	public ClientShutdownMessage()
	{
	
	}
	
	public String toString()
	{
		return "Shut down client!";
	}
}
