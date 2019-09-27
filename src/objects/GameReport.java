package objects;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Vector;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import server.ServerSettings;

public class GameReport implements Serializable
{
	private static final long serialVersionUID = 3047126779091553002L;
	
	private int             gameID;
	private int             round;
	private Map             map;
	private Bot             reportingBot;
	private Bot             opponentBot;
	private boolean         isHost;
	private String          address;
	private String          opponentAddress;
	
	private GameEndType     gameEndType;
	private boolean         won;
	private long            time; //time in ms measured by TM client
	private int             score;
	private boolean         crash;
	private String          crashLog;
	private Vector<Integer> timers;
	private boolean         gameTimeout;
	private int             finalFrame;
	
	private String          startDate;
	private String          finishDate;
	
	public GameReport(int gameID, int round, Map map, Bot reportingBot, Bot opponentBot, boolean isHost) 
	{
		this.gameID = gameID;
		this.round = round;
		this.map = map;
		this.reportingBot = reportingBot;
		this.opponentBot = opponentBot;
		this.isHost = isHost;
		
		won = false;
		time = 0;
		score = 0;
		crash = false;
		crashLog = "";
		timers = new Vector<Integer>();
		gameTimeout = false;
		finalFrame = 0;
		
		startDate = getCurrentTime();
		finishDate = "unknown";
	}

	public String getResultJSON(Vector<Integer> timerLimits) 
	{
		JsonObject resultObject = Json.object();
		
		resultObject.add("gameID", gameID);
		resultObject.add("round",  round);
		resultObject.add("map", map.getMapName());
		resultObject.add("reportingBot", reportingBot.getName());
		resultObject.add("opponentBot", opponentBot.getName());
		resultObject.add("wasHost", isHost);
		resultObject.add("address", address);
		resultObject.add("opponentAddress", opponentAddress);
		
		resultObject.add("gameEndType", gameEndType.toString());
		resultObject.add("won", won);
		resultObject.add("gameDuration", time);
		resultObject.add("score", score);
		resultObject.add("crash", crash);
		if (!crashLog.equals("") && ServerSettings.Instance().LadderMode)
		{
			resultObject.add("crashLog", crashLog);
		}
		resultObject.add("gameTimeout", gameTimeout);
		resultObject.add("finalFrame", finalFrame);
		
		//timerLimits are in the same order as the frame counts output by the TournamentModule
		JsonArray timersArray = (JsonArray) Json.array();
		for (int i = 0; i < timerLimits.size() && i < timers.size(); i++)
		{
			JsonObject timerObject = Json.object();
			timersArray.add(timerObject.add("timeInMS", timerLimits.get(i)).add("frameCount", timers.get(i)));
		}
		resultObject.add("timers", timersArray);
		
		resultObject.add("startDate", startDate);
		resultObject.add("finishDate", finishDate);
		
		return resultObject.toString();
	}
	
	public String getCrashLog()
	{
		if (!crashLog.equals(""))
		{
			return crashLog;
		}
		return null;		
	}
	
	public void setAddress(String address)
	{
		this.address = address;
	}
	
	public void setOpponentAddress(String address)
	{
		this.opponentAddress = address;
	}
	
	public String getaddress()
	{
		return address;		
	}

	public Bot getReportingBot()
	{
		return reportingBot;
	}
	
	public Bot getOpponentBot()
	{
		return opponentBot;
	}

	public Map getMap()
	{
		return map;
	}

	public boolean isWon()
	{
		return won;
	}

	public void setWon(boolean won)
	{
		this.won = won;
	}

	public int getGameID()
	{
		return gameID;
	}

	public int getScore()
	{
		return score;
	}

	public void setScore(int score)
	{
		this.score = score;
	}

	public boolean isCrash()
	{
		return crash;
	}

	public void setCrash(boolean crash)
	{
		this.crash = crash;
	}
	
	public void setCrashLog(String text)
	{
		this.crashLog = text;
	}

	public Vector<Integer> getTimers() 
	{
		return timers;
	}

	public void setTimers(Vector<Integer> timers) 
	{
		this.timers = timers;
	}

	public boolean isGameTimeout() {
		return gameTimeout;
	}

	public void setGameTimeout(boolean gameTimeout)
	{
		this.gameTimeout = gameTimeout;
	}

	public int getFinalFrame() {
		return finalFrame;
	}

	public void setFinalFrame(int finalFrame)
	{
		this.finalFrame = finalFrame;
	}

	public int getRound()
	{
		return round;
	}

	public long getTime()
	{
		return time;
	}

	public void setTime(long time)
	{
		this.time = time;
	}

	public String getFinishDate()
	{
		return finishDate;
	}

	public void setFinishDate()
	{
		finishDate = getCurrentTime();
	}

	public String getStartDate()
	{
		return startDate;
	}
	
	private String getCurrentTime() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(tz);
		return df.format(new Date());
	}

	public boolean isHost()
	{
		return isHost;
	}

	public GameEndType getGameEndType()
	{
		return gameEndType;
	}

	public void setGameEndType(GameEndType gameEndType)
	{
		this.gameEndType = gameEndType;
	}
}
