package objects;

import java.util.Vector;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.WriterConfig;

import server.ServerSettings;

public class GameResult implements Comparable<Object>
{
	public int gameID				= -1;
	public int roundID				= -1;
	
	public String hostName			= "Error";
	public String awayName			= "Error";
	public String mapName			= "Error";
	
	public boolean firstReport      = false;
	public boolean complete         = false;
	
	public String winName			= "Error";
	public String crashName			= "";
	public String timeOutName		= "";
	public GameEndType gameEndType  = GameEndType.NORMAL;
	
	public boolean hostWon			= false;
	public boolean hostCrash		= false;
	public boolean awayCrash		= false;
	
	public Vector<Integer> hostTimers   = new Vector<Integer>();
	public Vector<Integer> awayTimers   = new Vector<Integer>();

	public boolean hourTimeout		= false;
	
	public int hostScore			= 0;
	public int awayScore			= 0;
	public int finalFrame			= -2;
	public int hostTime				= 0;
	public int awayTime				= 0;
	
	public String hostAddress		= "unknown";
	public String awayAddress		= "unknown";
	public String startDate			= "unknown";
	public String finishDate		= "unknown"; 

	public GameResult() {}

	public GameResult (JsonObject result) 
	{
		setResult(result);

		/*int numTimers = ServerSettings.Instance().tmSettings.TimeoutLimits.size();
	
		for (int i=0; i<numTimers; ++i)
		{
			hostTimers.add(0);
			awayTimers.add(0);
		}*/
	}
	
	public void setResult (JsonObject result)
	{
		gameID = result.get("gameID").asInt();
		roundID = result.get("round").asInt();
		mapName = result.get("map").asString();
		Vector<Integer> timerVec;
		
		if (result.get("wasHost").asBoolean())
		{
			hostName = result.get("reportingBot").asString();
			hostWon = result.get("won").asBoolean();
			hostScore = result.get("score").asInt();
			hostTime = result.get("gameDuration").asInt();
			hostAddress = result.get("address").asString();
			timerVec = hostTimers;
			
			//if both bots have reported and crashed, believe the first to report
			hostCrash = result.get("crash").asBoolean();
			if (hostCrash && awayCrash)
			{
				hostWon = true;
				hostCrash = false;
			}
		}
		else
		{
			awayName = result.get("reportingBot").asString();
			hostWon = !result.get("won").asBoolean();
			awayScore = result.get("score").asInt();
			awayTime = result.get("gameDuration").asInt();
			awayAddress = result.get("address").asString();
			timerVec = awayTimers;
			
			//if both bots have reported and crashed, believe the first to report
			awayCrash = result.get("crash").asBoolean();
			if (hostCrash && awayCrash)
			{
				hostWon = false;
				awayCrash = false;
			}
		}
		
		//see GameEndType for precedence order of game end types
		GameEndType thisGameEnd = GameEndType.valueOf(result.get("gameEndType").asString());
		gameEndType = thisGameEnd.ordinal() < gameEndType.ordinal() ? thisGameEnd : gameEndType;
		
		hourTimeout = result.get("timeout").asBoolean();
		finalFrame = result.get("finalFrame").asInt() > finalFrame ? result.get("finalFrame").asInt() : finalFrame;
		
		JsonArray timers = result.get("timers").asArray();
		int numTimers = ServerSettings.Instance().tmSettings.TimeoutLimits.size();
		for (int i = 0; i < timers.size(); i++)
		{
			timerVec.add(timers.get(i).asObject().get("frameCount").asInt());
		}
		while (timerVec.size() < numTimers)
		{
			// if the results didn't contain enough timers, add -1s. Probably a crash.
			timerVec.add(-1);
		}
		
		// TODO: decide which bot's dates to use?
		startDate = result.get("startDate").asString();
		finishDate = result.get("finishDate").asString();
		
		// Only process final results if both bots have reported
		if (!firstReport)
		{
			firstReport = true;
		}
		else
		{
			// set name of crashing bot
			// at this point only one bot can be set to have crashed because of above logic
			if (hostCrash)
			{
				crashName = hostName;
				hostWon = false;
			}
			else if (awayCrash)
			{
				crashName = awayName;
				hostWon = true;
			}
			
			// check bot time-outs
			for (int i = 0; i < numTimers; ++i)
			{
				// check if the host timed out
				if (hostTimers.get(i) >= ServerSettings.Instance().tmSettings.TimeoutBounds.get(i))
				{
					timeOutName = hostName;
					hostWon = false;
					break;
				}
				
				// check if the away bot timed out
				if (awayTimers.get(i) >= ServerSettings.Instance().tmSettings.TimeoutBounds.get(i))
				{
					timeOutName = awayName;
					hostWon = true;
					break;
				}
			}
			
			// check if the bots reached the hour time limit
			// don't use the timeout boolean from the client report because it might be wrong (TournamentModule bug)
			if (finalFrame >= ServerSettings.Instance().tmSettings.GameFrameLimit)
			{
				hourTimeout = true;
				
				// the winner is the bot with the highest score
				if (hostScore >= awayScore)
				{
					hostWon = true;
				}
				else
				{
					hostWon = false;
				}
			}
			
			//set winner
			winName = hostWon ? hostName : awayName;
			complete = true;
		}
	}
	
	public String toString()
	{
		String s = String.format("%7d %5d %15s %15s %15s %8d %15s %15s %25s %6b %6b%6b %8d %8d %10d %10d \n",
				this.gameID, this.roundID, this.hostName, 
				this.awayName, this.winName, this.finalFrame, this.crashName, this.timeOutName, this.mapName, 
				this.hostCrash, this.awayCrash, 
				this.hourTimeout, this.hostScore, this.awayScore, 
				this.hostTime, this.awayTime);
				
		for (int i=0; i<hostTimers.size(); ++i)
		{
			String t = String.format(" %5d", hostTimers.get(i));
			s += t;
		}
		
		for (int i=0; i<awayTimers.size(); ++i)
		{
			String t = String.format(" %5d", awayTimers.get(i));
			s += t;
		}
		
		return s;
	}
	
	public String getResultString()
	{
		String winnerName = hostWon ? hostName : awayName;
		String loserName = hostWon ? awayName : hostName;
		
		String s = String
				.format("%7d %5d %15s %15s %15s %15s %25s %8d %8d %8d",
						gameID, 
						roundID,
						winnerName,
						loserName,
						crashName.length() == 0 ? "-" : crashName,
						timeOutName.length() == 0 ? "-" : timeOutName,
						mapName,
						finalFrame, 
						hostWon ? hostScore : awayScore, 
						hostWon ? awayScore : hostScore);

		for (int i=0; i<hostTimers.size(); ++i)
		{
			String t = String.format(" %7d", hostTimers.get(i));
			s += t;
		}
		
		for (int i=0; i<awayTimers.size(); ++i)
		{
			String t = String.format(" %7d", awayTimers.get(i));
			s += t;
		}
		
		s += "  " + (hostWon ? hostAddress : awayAddress) + "  " + (hostWon ? awayAddress : hostAddress);
		s += "  " + startDate + "  " + finishDate;
		
		return s;
	}
	
	public int compareTo(Object other)
	{
		return this.gameID - ((GameResult)other).gameID;
	}

	public String getHostReplayName()
	{
		String replayName = "";
		
		replayName += hostName.toUpperCase() + "\\";
		
		String idString = "" + gameID;
		while(idString.length() < 5) { idString = "0" + idString; }
		
		replayName += idString + "-";
		replayName += hostName.substring(0, Math.min(hostName.length(), 4)).toUpperCase();
		replayName += "_";
		replayName += awayName.substring(0, Math.min(awayName.length(), 4)).toUpperCase();
		replayName += ".REP";
		
		return replayName;
	}
	
	public String getAwayReplayName()
	{
		String replayName = "";
		
		replayName += awayName.toUpperCase() + "\\";
		String idString = "" + gameID;
		while(idString.length() < 5) { idString = "0" + idString; }
		
		replayName += idString + "-";
		replayName += awayName.substring(0, Math.min(awayName.length(), 4)).toUpperCase();
		replayName += "_";
		replayName += hostName.substring(0, Math.min(hostName.length(), 4)).toUpperCase();
		replayName += ".REP";
		
		return replayName;
	}
}
