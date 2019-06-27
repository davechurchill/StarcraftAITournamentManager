package objects;

import server.ServerSettings;

//This class should contain data that needs to be sent to each client just once after it connects to the server 
public class InitialSettingsMessage implements Message
{
	private static final long serialVersionUID = 1L;
	public LobbyGameSpeed lobbyGameSpeed;
	
	public InitialSettingsMessage()
	{
		lobbyGameSpeed = ServerSettings.Instance().lobbyGameSpeed;
	}
	
	public String toString()
	{
		return "Initial settings message: lobbyGameSpeed=" + lobbyGameSpeed.toString(); 
	}
}

