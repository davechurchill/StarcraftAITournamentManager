package utility;

import java.util.*;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;

import java.io.*;
import java.text.*;

import objects.Bot;
import objects.Game;
import objects.GameEndType;
import objects.GameResult;
import objects.TournamentModuleSettingsMessage;
import server.ServerSettings;

public class ResultsParser
{
	private HashMap<Integer, GameResult> gameResults = new HashMap<Integer, GameResult>();
	
	Vector<GameResult> results 		= new Vector<GameResult>();
	Set<Integer> gameIDs 			= new HashSet<Integer>();
	
	private Vector<Bot> botVector;
	
	private int numBots;
	private int numMaps;
	
	private String[] botNames;
	private String[] mapNames;
	
	private int[][] wins;
	private int[][] mapWins;
	private int[][] mapGames;
	
	private int eloK = 32;
	private double[] elo;
	private int[] timeout;
	private int[] games;
	private int[] allWins;
	private int[] crash;
	private int[] frames;
	private int[] realSeconds;
	private int[] mapUsage;
	private int[] hour;
	
	//first index is bot index, second index is round number
	private Vector<Vector<Integer>> winsAfterRound = new Vector<Vector<Integer>>();
	private Vector<Vector<Integer>> gamesAfterRound = new Vector<Vector<Integer>>();
	
	public ResultsParser(String filename)
	{
		// get number of bots excluding any excluded bots
		botVector = new Vector<Bot>();
		for (Bot bot : ServerSettings.Instance().BotVector)
		{
			if (!ServerSettings.Instance().isExcludedBot(bot.getName()))
			{
				botVector.add(bot);
			}
		}
		numBots = botVector.size();
		numMaps = ServerSettings.Instance().MapVector.size();
		
		botNames    = new String[numBots];
		mapNames    = new String[numMaps];
		
		wins        = new int[numBots][numBots];
		mapWins     = new int[numBots][numMaps];
		mapGames    = new int[numBots][numMaps];
		
		elo         = new double[numBots];
		timeout     = new int[numBots];
		games       = new int[numBots];
		allWins     = new int[numBots];
		crash       = new int[numBots];
		frames      = new int[numBots];
		realSeconds = new int[numBots];
		mapUsage    = new int[numMaps];
		hour        = new int[numBots];
		
		// set the bot names and map names
		for (int i=0; i<botNames.length; ++i)
		{
			elo[i] = 1200;
			botNames[i] = botVector.get(i).getName();
			
		}
		
		for (int i=0; i<mapNames.length; ++i)
		{
			mapNames[i] = ServerSettings.Instance().MapVector.get(i).getMapName();
		}
		
		try
		{
			if (!new File(filename).exists())
			{
				return;
			}
		
			BufferedReader br = new BufferedReader(new FileReader(filename));
		
			String line;
			
			while ((line = br.readLine()) != null)
			{
				parseLine(line);
			}
			
			results = new Vector<GameResult>(gameResults.values());
			Collections.sort(results, new GameResultIDComparator());
			
			parseResults(results);
			br.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
		
	public int numResults()
	{
		return results.size();
	}
		
	public void parseResults(Vector<GameResult> results) throws Exception
	{
		for (int i=0; i<numBots; ++i)
		{
			gamesAfterRound.add(new Vector<Integer>());
			winsAfterRound.add(new Vector<Integer>());
			
			gamesAfterRound.get(i).add(0);
			winsAfterRound.get(i).add(0);
		}
		
		for (int i=0; i<results.size(); i++)
		{
			GameResult result = results.get(i);
			
			// if the game didn't start for either bot, don't parse this result
			// if all bots have not reported, don't parse this result
			if (result.finalFrame <= 0 || !result.complete || result.gameEndType == GameEndType.GAME_STATE_NOT_UPDATED_60S_BOTH_BOTS)
			{
				continue;
			}
			
			try
			{
				if (result.timeout != -1)
				{
					timeout[getIndex(result.getTimeoutName())]++;
				}
				
				if (result.crash != -1)
				{
					crash[getIndex(result.getCrashName())]++;
				}
				
				int winner = getIndex(result.getWinnerName());
				int map = getMapIndex(result.map);
				mapUsage[map]++;
				mapWins[winner][map]++;
				
				for (int j = 0; j < result.bots.size(); j++)
				{
					int b1 = getIndex(result.bots.get(j));
					hour[b1] += (result.gameTimeout) ? 1 : 0;
					games[b1]++;
					frames[b1] += result.finalFrame;
					long gameLengthMS = result.times.get(0) > result.times.get(1) ? result.times.get(0) : result.times.get(1);  
					realSeconds[b1] += gameLengthMS / 1000;
					mapGames[b1][map]++;
					
					//record all results for combinations of bots in the game
					for (int k = j + 1; k < result.bots.size(); k++)
					{
						int b2 = getIndex(result.bots.get(k));
						wins[b1][b2] += (result.winner == j) ? 1 : 0;
						wins[b2][b1] += (result.winner == k) ? 1 : 0;
						updateElo(b1, b2, winner == b1);
					}
					
					// update win percentage arrays
					// if it's a new round, add it to the end of the vector
					if (result.roundID >= gamesAfterRound.get(b1).size())
					{
						gamesAfterRound.get(b1).add(gamesAfterRound.get(b1).lastElement() + 1);
					}
					// otherwise just add 1 to the back
					else
					{
						gamesAfterRound.get(b1).set(result.roundID, gamesAfterRound.get(b1).get(result.roundID) + 1);
					}
					
					while (result.roundID >= winsAfterRound.get(b1).size())
					{
						winsAfterRound.get(b1).add(winsAfterRound.get(b1).lastElement());		
					}	
				}
				winsAfterRound.get(winner).set(result.roundID, winsAfterRound.get(winner).get(result.roundID) + 1);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new Exception("Exception while parsing results for game " + result.gameID);
			}
		}
		
		for (int i=0; i<numBots; i++)
		{
			for (int j=0; j<numBots; j++)
			{
				allWins[i] += wins[i][j];
			}
		}
	}
	
	private void updateElo(int b1, int b2, boolean win)
	{
		double e1 = 1.0 / (1 + Math.pow(10,  (elo[b2]-elo[b1])/400.0) );
		int w1 = win ? 1 : 0;
		double newElo1 = elo[b1] + eloK * (w1 - e1);
		
		double e2 = 1.0 / (1 + Math.pow(10,  (elo[b1]-elo[b2])/400.0) );
		int w2 = win ? 0 : 1;
		double newElo2 = elo[b2] + eloK * (w2 - e2);
		
		elo[b1] = newElo1;
		elo[b2] = newElo2;
	}
	
	public void writeWinPercentageGraph()
	{
		Vector<ResultPair> allPairs = new Vector<ResultPair>();
		for (int i=0; i<numBots; i++)
		{
			double winPercentage = (games[i] > 0 ? ((double)allWins[i]/games[i]) : 0);
			allPairs.add(new ResultPair(botNames[i], i, winPercentage));
		}
		
		Collections.sort(allPairs, new ResultPairComparator());
		
		StringBuilder winPercentage = new StringBuilder();
		StringBuilder roundWins = new StringBuilder();
		
		winPercentage.append("var winPercentage = [\n");
		roundWins.append("var roundWins = [\n");
		
		for (int i=0; i<numBots; ++i)
		{
			// only write the stats for this bot if it has completed at least one game
			if (games[i] > 0)
			{
				int ii = allPairs.get(i).botIndex;
				
				winPercentage.append("{\"name\": \"" + botNames[ii] + "\", \"data\": [");
				roundWins.append("{\"name\": \"" + botNames[ii] + "\", \"data\": [");
				for (int j=0; j<gamesAfterRound.get(ii).size(); ++j)
				{
					double winRate = (double)winsAfterRound.get(ii).get(j) / (double)gamesAfterRound.get(ii).get(j);
					
					winPercentage.append(" " + winRate);
					
					int wins = winsAfterRound.get(ii).get(j);
					if (j > 0)
					{
						wins -= winsAfterRound.get(ii).get(j-1);
					}
					
					int games = gamesAfterRound.get(ii).get(j);
					if (j > 0)
					{
						games -= gamesAfterRound.get(ii).get(j-1);
					}
					
					double roundWinRate = (double)wins / (double)games;
					
					roundWins.append(" " + roundWinRate);
					
					if (j < gamesAfterRound.get(ii).size() - 1)
					{
						winPercentage.append(",");
						roundWins.append(",");
					}
				}
				winPercentage.append("] }");
				roundWins.append("] }");
				
				if (i < numBots - 1)
				{
					winPercentage.append(",");
					roundWins.append(",");
				}
				
				winPercentage.append("\n");
				roundWins.append("\n");
			}
		}
		
		winPercentage.append("];");
		roundWins.append("];");
		
		FileUtils.CreateDirectory("html/results");
		FileUtils.writeToFile(winPercentage.toString(), "html/results/winpercentage.js", false);
		FileUtils.writeToFile(roundWins.toString(), "html/results/roundwins.js", false);
	}
	
	public boolean hasGameResult(int gameID)
	{
		return gameIDs.contains(gameID);
	}
	
	public void writeDetailedResults()
	{
		StringBuilder out = new StringBuilder();
		StringBuilder outtxt = new StringBuilder();
		
		//text file header line
		outtxt.append(GameResult.getResultStringHeader() + "\n");
		
		//replay dir on local machine, easy to change by editing the output file
		out.append("var replayPath = '../replays/';\n");
		
		//details of the frame time limits
		String frameLimits = "var frameLimits = [";
		TournamentModuleSettingsMessage tmSettings =  ServerSettings.Instance().tmSettings;
		// looks like [{"timeInMS":55,"frameCount":320},{"timeInMS":1000,"frameCount":10},{"timeInMS":10000,"frameCount":1}]
		for (int i = 0; i < tmSettings.TimeoutLimits.size(); i++)
		{
			frameLimits += "{\"timeInMS\":" + tmSettings.TimeoutLimits.get(i) + ",\"frameCount\":" + tmSettings.TimeoutBounds.get(i) + "}";
			if (i != tmSettings.TimeoutLimits.size() - 1) {
				frameLimits += ",";
			}
		}
		frameLimits += "];\n";
		out.append(frameLimits);
		
		out.append("var detailedResults = [\n");
		
		for (int i=0; i<results.size(); i++)
		{				
			GameResult r = results.get(i);
			
			out.append(r.getResultJSON());
			if (i != results.size() - 1)
			{
				out.append(",");
			}
			out.append("\n");
			
			outtxt.append(r.getResultString() + "\n");
		}
		
		out.append("];");
		
		FileUtils.CreateDirectory("html/results");
		FileUtils.writeToFile(out.toString(), "html/results/detailed_results_json.js", false);
		FileUtils.writeToFile(outtxt.toString(), "html/results/detailed_results.txt", false);
	}
	
	public String getResultsSummary()
	{	
		Vector<ResultPair> allPairs = new Vector<ResultPair>();
		for (int i=0; i<numBots; i++)
		{
			double winPercentage = (games[i] > 0 ? ((double)allWins[i]/games[i]) : 0);
			allPairs.add(new ResultPair(botNames[i], i, winPercentage));
		}
		Collections.sort(allPairs, new ResultPairComparator());
		
		StringBuilder out = new StringBuilder();
		
		File resultsTXT = new File("html/results/detailed_results.txt");
		out.append("var resultsTXTSize = " + (resultsTXT.exists() ? resultsTXT.length()/1000 : 0) + ";\n");
		out.append("var lastUpdateTime = '" + new SimpleDateFormat("yyyy-MM-dd [HH:mm:ss]").format(Calendar.getInstance().getTime()) + "';\n");
		
		JsonArray maps = (JsonArray) Json.array();
		for (String mapName : mapNames)
		{
			maps.add(mapName);
		}
		out.append("var maps = " + maps.toString() + ";\n");
		
		JsonArray resultsSummary = (JsonArray) Json.array();
		
		for (int i=0; i<numBots; ++i)
		{
			int ii = allPairs.get(i).botIndex;
		
			JsonObject botSummary = Json.object();
			botSummary.add("BotName", botNames[ii]);
			botSummary.add("Rank", i);
			botSummary.add("Race", botVector.get(ii).getRace());
			
			//parse BWAPI version to go from "BWAPI_412" -> "4.1.2"
			String version = botVector.get(ii).getBWAPIVersion();
			version = version.replaceFirst("BWAPI_", "");
			String versionNum = "";
			for (int j = 0; j < version.length(); j++)
			{
				versionNum += version.charAt(j);
				if (j != version.length() - 1)
				{
					versionNum += ".";
				}
			}
			botSummary.add("BWAPIVersion", versionNum);
			
			botSummary.add("Games", games[ii]);
			botSummary.add("Wins", allWins[ii]);
			botSummary.add("Losses", games[ii] - allWins[ii]);
			botSummary.add("Score", new DecimalFormat("##.##").format(allPairs.get(i).win*100));
			botSummary.add("ELO", (int)elo[ii]);
			botSummary.add("AvgTime", games[ii] > 0 ? frames[ii]/games[ii] : 0);
			botSummary.add("WallTime", games[ii] > 0 ? realSeconds[ii]/games[ii] : 0);
			botSummary.add("Hour", hour[ii]);
			botSummary.add("Crash", crash[ii]);
			botSummary.add("Timeout", timeout[ii]);
			
			JsonArray resultPairs = (JsonArray) Json.array();
			for (int j=0; j<numBots; j++)
			{
				int jj = allPairs.get(j).botIndex;
				
				JsonObject pair = Json.object();				
				pair.add("Opponent", botNames[jj]);
				pair.add("Wins", wins[ii][jj]);
				pair.add("Games", wins[ii][jj] + wins[jj][ii]);
				resultPairs.add(pair);
			}
			botSummary.add("resultPairs", resultPairs);
			
			JsonArray mapResults = (JsonArray) Json.array();
			for (int j=0; j<numMaps; j++)
			{
				JsonObject mapResult = Json.object();
				mapResult.add("Map", mapNames[j].substring(mapNames[j].indexOf(')') + 1, mapNames[j].indexOf('.')));
				mapResult.add("Wins", mapWins[ii][j]);
				mapResult.add("Games", mapGames[ii][j]);
				mapResults.add(mapResult);
			}
			botSummary.add("mapResults", mapResults);
			
			resultsSummary.add(botSummary);
		}
		out.append("var resultsSummary = " + resultsSummary.toString(WriterConfig.PRETTY_PRINT) + ";\n");
		
		return out.toString();
	}
		
	public int getIndex(String botName) throws Exception
	{
		for (int i=0; i<botNames.length; i++)
		{
			if (botNames[i].equals(botName))
			{
				return i;
			}
		}
		throw new Exception("Bot '" + botName + "' not found.");
	}
	
	public int getMapIndex(String mapName) throws Exception
	{
		for (int i=0; i<mapNames.length; i++)
		{
			if (mapNames[i].equals(mapName))
			{
				return i;
			}
		}
		throw new Exception("Map '" + mapName + "' not found.");
	}
	
	public Set<Integer> getGameIDs() {
		return gameIDs;
	}
	
	public void parseLine(String line)
	{
		if (line.trim().length() > 0)
		{
			JsonObject result = Json.parse(line).asObject();
			
			int gameID = result.get("gameID").asInt();
			gameIDs.add(gameID);
			
			//filter for excluded bots
			for (String excludedBot : ServerSettings.Instance().ExcludeFromResults)
			{
				if (excludedBot.equals(result.get("reportingBot").asString()) || excludedBot.equals(result.get("opponentBot").asString()))
				{
					return;
				}
			}
			
			if (gameResults.containsKey(gameID))
			{
				gameResults.get(gameID).setResult(result);
			}
			else
			{
				gameResults.put(gameID, new GameResult(result));
			}
		}
	}
}

class ResultPair
{
	public String botName;
	public int botIndex;
	public double win;

	public ResultPair(String botName, int botIndex, double win)
	{
		this.botName = botName;
		this.botIndex = botIndex;
		this.win = win;
	}
	
}

class GameResultIDComparator implements Comparator<GameResult>
{
    public int compare(GameResult o1, GameResult o2)
    {
		return new Integer(o1.gameID).compareTo(new Integer(o2.gameID));
	}
}

class ResultPairComparator implements Comparator<ResultPair>
{
    public int compare(ResultPair o1, ResultPair o2)
    {
		if (o1.win == o2.win) return 0;
		if (o1.win < o2.win) return 1;
		return -1;
	}
}

class GameResultCrashComparator implements Comparator<GameResult>
{
    public int compare(GameResult o1, GameResult o2)
    {
		return o1.getCrashName().compareTo(o2.getCrashName());
	}
}

class GameResultTimeOutComparator implements Comparator<GameResult>
{
    public int compare(GameResult o1, GameResult o2)
    {
		return o1.getTimeoutName().compareTo(o2.getTimeoutName());
	}
}

class GameWinnerNameComparator implements Comparator<GameResult>
{
    public int compare(GameResult o1, GameResult o2)
    {
		return o1.getWinnerName().compareTo(o2.getWinnerName());
	}
}