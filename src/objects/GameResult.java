package objects;

import java.util.Vector;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import server.ServerSettings;

public class GameResult implements Comparable<Object>
{
	public int gameID				= -1;
	public int roundID				= -1;
	
	public Vector<String> bots            = new Vector<String>();
	public Vector<String> replays         = new Vector<String>();
	public Vector<Integer> scores         = new Vector<Integer>();
	public Vector<String> addresses       = new Vector<String>();
	public Vector<Vector<Integer>> timers = new Vector<Vector<Integer>>();
	public Vector<Integer> times          = new Vector<Integer>(); //time in ms measured by TM client - not used currently
	
	public int host = -1;
	public int winner = -1;
	public int crash = -1;
	public int timeout = -1;
	public String map = "";
	public int finalFrame = -1;
	public GameEndType gameEndType  = GameEndType.NORMAL;
	public boolean gameTimeout		= false;
	public String startDate			= "unknown";
	public String finishDate		= "unknown";
	
	public int reportsReceived      = 0;
	public boolean complete         = false;

	public GameResult (JsonObject result) 
	{
		setResult(result);
	}
	
	public void setResult (JsonObject result)
	{
		gameID = result.get("gameID").asInt();
		roundID = result.get("round").asInt();
		map = result.get("map").asString();
		
		int botIndex = getIndex(result.get("reportingBot").asString());
		if (botIndex == -1)
		{
			initBot(result.get("reportingBot").asString());
			botIndex = getIndex(result.get("reportingBot").asString());
		}
		
		int oppIndex = getIndex(result.get("opponentBot").asString());
		if (oppIndex == -1)
		{
			initBot(result.get("opponentBot").asString());
			oppIndex = getIndex(result.get("opponentBot").asString());
		}
		
		scores.set(botIndex, result.get("score").asInt());
		times.set(botIndex, result.get("gameDuration").asInt()); //unused
		addresses.set(botIndex, result.get("address").asString());
		addresses.set((botIndex + 1) % 2, result.get("opponentAddress").asString());
		replays.set(botIndex, getReplayName(botIndex));
		
		if (result.get("wasHost").asBoolean())
		{
			host = botIndex;
		}
		else
		{
			host = (botIndex + 1) % 2;
		}
		
		if (result.get("won").asBoolean())
		{
			winner = botIndex;
		}
		
		//if this is first bot to report crash, assign crash
		if (result.get("crash").asBoolean() && crash == -1)
		{
			crash = botIndex;
		}
		
		//see GameEndType source for precedence order of game end types
		GameEndType thisGameEnd = GameEndType.valueOf(result.get("gameEndType").asString());
		if (gameEndType == GameEndType.GAME_STATE_NOT_UPDATED_60S && thisGameEnd == GameEndType.GAME_STATE_NOT_UPDATED_60S)
		{
			gameEndType = GameEndType.GAME_STATE_NOT_UPDATED_60S_BOTH_BOTS;
		}
		else
		{
			gameEndType = thisGameEnd.ordinal() < gameEndType.ordinal() ? thisGameEnd : gameEndType;
		}
		
		//if this bot reports that StarCraft didn't start or crashed before the game, assign it as crashing bot
		if (thisGameEnd == GameEndType.STARCRAFT_NEVER_DETECTED)
		{
			crash = botIndex;
		}
		
		gameTimeout = result.get("gameTimeout").asBoolean();
		finalFrame = result.get("finalFrame").asInt() > finalFrame ? result.get("finalFrame").asInt() : finalFrame;
		
		Vector<Integer> timerVec = new Vector<Integer>();
		JsonArray botTimers = result.get("timers").asArray();
		int numTimers = ServerSettings.Instance().tmSettings.TimeoutLimits.size();
		for (int i = 0; i < botTimers.size(); i++)
		{
			timerVec.add(botTimers.get(i).asObject().get("frameCount").asInt());
		}
		while (timerVec.size() < numTimers)
		{
			// if the results didn't contain enough timers, add -1s. Probably a crash.
			timerVec.add(-1);
		}
		timers.set(botIndex, timerVec);
		
		// TODO: decide which bot's dates to use?
		startDate = result.get("startDate").asString();
		finishDate = result.get("finishDate").asString();
		
		// Only process final results if all bots have reported
		if (reportsReceived == 0)
		{
			reportsReceived++;
		}
		else
		{
			// with gameEndTypes crashing bot should be correctly assigned by this time
			// but for testing old results converted to new format, crash type is STARCRAFT_CRASH
			// in old format when final frame is -1 for both bots, the second bot to report is chosen as crasher/loser
			// this only affects detailed results, not result summary, since games that never started are not counted
			if (gameEndType == GameEndType.STARCRAFT_CRASH && finalFrame == -1)
			{
				gameEndType = GameEndType.GAME_STATE_NEVER_DETECTED;
				crash = 1;
			}
			
			// set index of winner based on crashing bot
			if (crash != -1)
			{
				winner = (crash + 1) % 2;
			}
			
			// check bot time-outs			
			for (int i = 0; i < bots.size(); i++)
			{
				for (int j = 0; j < timers.get(i).size(); ++j)
				{
					if (timers.get(i).get(j) >= ServerSettings.Instance().tmSettings.TimeoutBounds.get(j))
					{
						timeout = i;
						winner = (timeout + 1) % 2;
						break;
					}
				}
			}
			
			// check if the bots reached the game time limit
			// don't use the timeout boolean from the client report because it might be wrong (TournamentModule bug)
			if (finalFrame >= ServerSettings.Instance().tmSettings.GameFrameLimit)
			{
				gameTimeout = true;
				
				// the winner is the bot with the highest score
				int maxScore = -1;
				int maxScoreIndex = -1;
				for (int i = 0; i < bots.size(); i++)
				{
					if (scores.get(i) > maxScore)
					{
						maxScore = scores.get(i);
						maxScoreIndex = i;
					}
				}
				winner = maxScoreIndex;
			}
			complete = true;
		}
	}
	
	public String getResultJSON() {
		JsonObject resultObject = Json.object();
		
		resultObject.add("gameID", gameID);
		resultObject.add("round",  roundID);
		
		JsonArray botsArray = (JsonArray) Json.array();
		for (String botName : bots)
		{
			botsArray.add(botName);
		}
		resultObject.add("bots", botsArray);
		
		JsonArray replaysArray = (JsonArray) Json.array();
		for (String replay : replays)
		{
			replaysArray.add(replay);
		}
		resultObject.add("replays", replaysArray);
		
		JsonArray scoresArray = (JsonArray) Json.array();
		for (int score : scores)
		{
			scoresArray.add(score);
		}
		resultObject.add("scores", scoresArray);
		
		JsonArray addressesArray = (JsonArray) Json.array();
		for (String address : addresses)
		{
			addressesArray.add(address);
		}
		resultObject.add("addresses", addressesArray);
		
		
		resultObject.add("host", host);
		resultObject.add("winner", winner);
		resultObject.add("crash", crash);
		resultObject.add("timeout", timeout);
		resultObject.add("map", map);
		
		// if only one client sent back a report, change gameEndType to show that.
		resultObject.add("gameEndType", (complete ? gameEndType.toString() : GameEndType.NO_REPORT.toString()));
		
		String hours   = "" + finalFrame/(24*60*60); while (hours.length() < 2) { hours = "0" + hours; }
		String minutes = "" + (finalFrame % (24*60*60))/(24*60); while (minutes.length() < 2) { minutes = "0" + minutes; }
		String seconds = "" + (finalFrame % (24*60))/(24); while (seconds.length() < 2) { seconds = "0" + seconds; }
		resultObject.add("duration", hours + ":" + minutes + ":" + seconds);
		
		Vector<Integer> timerLimits = ServerSettings.Instance().tmSettings.TimeoutLimits;
		JsonArray timersArrayArray = (JsonArray) Json.array();
		for (int i = 0; i < bots.size(); i++)
		{
			//timerLimits are in the same order as the frame counts output by the TournamentModule
			JsonArray timersArray = (JsonArray) Json.array();
			for (int j = 0; j < timerLimits.size(); j++)
			{
				JsonObject timerObject = Json.object();
				timersArray.add(timerObject.add("timeInMS", timerLimits.get(j)).add("frameCount", timers.get(i).get(j)));
			}
			timersArrayArray.add(timersArray);
		}
		resultObject.add("timers", timersArrayArray);
		
		double maxScore = Math.max(scores.get(0), scores.get(1)) + 1;
		double closeness = 0;
		if (scores.size() > 1 && winner != -1)
		{
			closeness = (scores.get(winner) - scores.get((winner + 1) % 2)) / maxScore;
		}
		 
		closeness = (double)Math.round(closeness * 100000) / 100000;
		resultObject.add("(W-L)/Max", closeness);
		
		resultObject.add("start", startDate);
		resultObject.add("finish", finishDate);
		
		return resultObject.toString();
	}
	
	public static String getResultStringHeader()
	{
		String s = String
				.format("%7s %6s %15s %15s %15s %15s %25s %11s %15s %8s %8s",
					"Game ID",
					"Round",
					"Winner",
					"Loser",
					"Crash",
					"Timeout",
					"Map",
					"Final Frame",
					"Game End Type",
					"W Score",
					"L Score"
			);
		
		Vector<Integer> timerLimits = ServerSettings.Instance().tmSettings.TimeoutLimits;
		for (int i = 0; i < timerLimits.size(); ++i)
		{
			String t = String.format(" %7s", "W " + timerLimits.get(i));
			s += t;
		}
		for (int i = 0; i < timerLimits.size(); ++i)
		{
			String t = String.format(" %7s", "L " + timerLimits.get(i));
			s += t;
		}
		
		s += String.format(" %15s", "W Address");
		s += String.format(" %15s", "L Address");
		
		s += String.format(" %15s", "Start");
		s += String.format(" %15s", "Finish");
		
		return s;
	}
	
	public String getResultString()
	{
		if (winner == -1)
		{
			if (!complete)
			{
				winner = 0;
				crash = 1;
			}
			else if (crash != -1)
			{
				winner = (crash + 1) % 2;
			}
			else if (finalFrame == -1)
			{
				winner = 0;
			}
			else
			{
				winner = 1;
			}
		}
		
		String winnerName = getWinnerName();
		String loserName = bots.get((winner + 1) % 2);
		
		String s = String
			.format("%7d %6d %15s %15s %15s %15s %25s %11d %15s %8d %8d",
				gameID, 
				roundID,
				winnerName,
				loserName,
				crash == -1 ? "-" : getCrashName(),
				timeout == -1 ? "-" : getTimeoutName(),
				map,
				finalFrame,
				gameEndType.toString(),
				scores.get(winner), 
				scores.get((winner + 1) % 2)
		);

		for (int i = 0; i < timers.get(host).size(); ++i)
		{
			String t = String.format(" %7d", timers.get(host).get(i));
			s += t;
		}
		
		for (int i = 0; i < timers.get((host + 1) % 2).size(); ++i)
		{
			String t = String.format(" %7d", timers.get((host + 1) % 2).get(i));
			s += t;
		}
		
		s += String.format(" %15s", addresses.get(winner));
		s += String.format(" %15s", addresses.get((winner + 1) % 2));
		
		s += String.format(" %15s", startDate);
		s += String.format(" %15s", finishDate);
		
		return s;
	}
	
	public String getHostName()
	{
		if (host != -1)
		{
			return bots.get(host);
		}
		return "";
	}
	
	public String getWinnerName()
	{
		if (winner != -1)
		{
			return bots.get(winner);
		}
		return "";
	}
	
	public String getCrashName()
	{
		if (crash != -1)
		{
			return bots.get(crash);
		}
		return "";
	}
	
	public String getTimeoutName()
	{
		if (timeout != -1)
		{
			return bots.get(timeout);
		}
		return "";
	}
	
	public int compareTo(Object other)
	{
		return this.gameID - ((GameResult)other).gameID;
	}
	
	private void initBot(String botName)
	{
		bots.add(botName);
		scores.add(-1);
		times.add(-1); //unused
		addresses.add("");
		replays.add("");
		Vector<Integer> timerVec = new Vector<Integer>();
		while (timerVec.size() < ServerSettings.Instance().tmSettings.TimeoutLimits.size())
		{
			timerVec.add(-1);
		}
		timers.add(timerVec);
	}
	
	private int getIndex(String botName)
	{
		for (int i = 0; i < bots.size(); i++)
		{
			if (bots.get(i).equals(botName))
			{
				return i;
			}
		}
		return -1;
	}

	private String getReplayName(int botIndex)
	{
		String replayName = "";
		
		
		replayName += bots.get(botIndex).toUpperCase() + "\\";
		
		String idString = "" + gameID;
		while(idString.length() < 5) { idString = "0" + idString; }
		
		replayName += idString + "-";
		replayName += bots.get(botIndex).substring(0, Math.min(bots.get(botIndex).length(), 4)).toUpperCase();
		replayName += "_";
		replayName += bots.get((botIndex + 1) % 2).substring(0, Math.min(bots.get((botIndex + 1) % 2).length(), 4)).toUpperCase();
		replayName += ".REP";
		
		return replayName;
	}
	
}
