package objects;

import java.io.Serializable;

public class Game implements Serializable
{
	private static final long serialVersionUID = 4555405442165116503L;
	
	private Bot 			homebot;
	private Bot 			awaybot;
	
	//private String          homeAddress;
	//private String          awayAddress;
	
	private GameReport      homeReport;
	private GameReport      awayReport;
	
	private GameStatus 		status;
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
		
		status = GameStatus.WAITING;
	}

	/*public void setFinishDate(String date)
	{
		finishDate = date;
	}
	
	public void setStartDate(String date)
	{
		startDate = date;		
	}*/
	
	public GameStatus getStatus() {
		return status;
	}

	public void setStatus(GameStatus status) {
		this.status = status;
	}

	public Bot getHomebot() {
		return homebot;
	}
	
	public Bot getAwaybot() {
		return awaybot;
	}

	/*public void setHomeAddress(String address) {
		homeAddress = address;
	}
	
	public void setAwayAddress(String address) {
		awayAddress = address;
	}*/
	
	public Map getMap() {
		return map;
	}

	public boolean isHostwon() {
		return homeReport.isWon();
	}

	public int getGameID() {
		return gameID;
	}

	/**
	 * @return the hostScore
	 */
	public int getHostScore() {
		return homeReport.getScore();
	}

	/**
	 * @return the awayScore
	 */
	public int getAwayScore() {
		return awayReport.getScore();
	}

	
	/**
	 * Getter for the boolean value indicating that the host bot has crashed
	 * 
	 * @return the hostcrash
	 */
	public boolean isHostcrash() {
		return homeReport.isCrash();
	}

	/**
	 * Getter for the boolean value indicating that the away bot has crashed
	 * 
	 * @return the awaycrash
	 */
	public boolean isAwaycrash() {
		return awayReport.isCrash();
	}

	/**
	 * @return the round
	 */
	public int getRound() {
		return round;
	}
		
	public void addReport(GameReport report)
	{
		if (report.isHost())
		{
			homeReport = report;
		}
		else
		{
			awayReport = report;
		}
		
		setStatus(GameStatus.DONE);
	}
}
