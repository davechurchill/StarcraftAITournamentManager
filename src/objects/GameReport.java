package objects;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;

public class GameReport implements Serializable
{
	private static final long serialVersionUID = 3047126779091553002L;
	
	private int             gameID;
	private int             round;
	private Map             map;
	private Bot             reportingBot;
	private boolean         isHost;
	private String          address;
	
	private GameEndType     gameEndType;
	private boolean         won;
	private long            time;
	private int             score;
	private boolean         crash;
	private Vector<Integer> timers;
	private boolean         timeout;
	private int             finalFrame;
	
	private String          startDate;
	private String          finishDate;
	
	public GameReport(int gameID, int round, Map map, Bot bot, boolean isHost, String address) 
	{
		this.gameID = gameID;
		this.round = round;
		this.map = map;
		reportingBot = bot;
		this.isHost = isHost;
		this.address = address;
		
		won = false;
		time = 0;
		score = 0;
		crash = false;
		timers = new Vector<Integer>();
		timeout = false;
		finalFrame = 0;
		
		startDate = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
		finishDate = "unknown";
	}


	public String getResultJSON(Vector<Integer> timerLimits) 
	{
		JsonObject resultObject = Json.object();
		
		resultObject.add("gameID", gameID);
		resultObject.add("round",  round);
		resultObject.add("map", map.getMapName());
		resultObject.add("reportingBot", reportingBot.getName());
		resultObject.add("wasHost", isHost);
		resultObject.add("address", address);
		
		resultObject.add("gameEndType", gameEndType.toString());
		resultObject.add("won", won);
		resultObject.add("gameDuration", time);
		resultObject.add("score", score);
		resultObject.add("crash", crash);
		resultObject.add("timeout", timeout);
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
	
	public String getaddress()
	{
		return address;		
	}

	public Bot getBot() {
		return reportingBot;
	}

	public Map getMap() {
		return map;
	}

	public boolean isWon() {
		return won;
	}

	public void setWon(boolean won) {
		this.won = won;
	}

	public int getGameID() {
		return gameID;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public boolean isCrash() {
		return crash;
	}

	public void setCrash(boolean crash) {
		this.crash = crash;
	}

	public Vector<Integer> getTimers() 
	{
		return timers;
	}

	public void setTimers(Vector<Integer> timers) 
	{
		this.timers = timers;
	}

	public boolean isTimeout() {
		return timeout;
	}

	public void setTimeout(boolean timeout) {
		this.timeout = timeout;
	}

	public int getFinalFrame() {
		return finalFrame;
	}

	public void setFinalFrame(int finalFrame) {
		this.finalFrame = finalFrame;
	}

	public int getRound() {
		return round;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public String getFinishDate()
	{
		return finishDate;
	}

	public void setFinishDate()
	{
		finishDate = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
	}

	public String getStartDate()
	{
		return startDate;
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
