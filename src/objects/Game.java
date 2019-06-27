package objects;

import java.io.Serializable;

public class Game implements Serializable
{
	private static final long serialVersionUID = 4555405442165116503L;
	
	private Bot 			homebot;
	private Bot 			awaybot;
	
	private String          homeAddress;
	private String          awayAddress;
	
	private Map 			map;

	private int 			gameID;
	private int 			round;

	//private String			finishDate = "unknown";
	//private String			startDate = "unknown";

	public Game(int iD, int round, Bot home, Bot away, Map map) 
	{
		gameID = iD;
		this.round = round;
		this.homebot = home;
		this.awaybot = away;
		this.map = map;
	}

	/*public void setFinishDate(String date)
	{
		finishDate = date;
	}
	
	public void setStartDate(String date)
	{
		startDate = date;		
	}*/
	
	public Bot getHomebot()
	{
		return homebot;
	}
	
	public Bot getAwaybot()
	{
		return awaybot;
	}

	public void setHomeAddress(String address)
	{
		homeAddress = address;
	}
	
	public void setAwayAddress(String address)
	{
		awayAddress = address;
	}
	
	public String getHomeAddress()
	{
		return homeAddress;
	}
	
	public String getAwayAddress()
	{
		return awayAddress;
	}
	
	public Map getMap()
	{
		return map;
	}

	public int getGameID()
	{
		return gameID;
	}

	/**
	 * @return the round
	 */
	public int getRound()
	{
		return round;
	}
}
