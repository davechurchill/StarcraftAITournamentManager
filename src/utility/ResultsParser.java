package utility;

import java.util.*;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.io.*;
import java.text.*;

import objects.GameResult;
import server.ServerSettings;

public class ResultsParser
{
	private HashMap<Integer, GameResult> gameResults = new HashMap<Integer, GameResult>();
	
	Vector<GameResult> results 		= new Vector<GameResult>();
	Set<Integer> gameIDs 			= new HashSet<Integer>();
	
	private int numBots 			= ServerSettings.Instance().BotVector.size();
	private int numMaps 			= ServerSettings.Instance().MapVector.size();
	
	private String[] botNames 		= new String[numBots];
	private String[] mapNames 		= new String[numMaps];
	
	private int[][] wins 			= new int[numBots][numBots];
	private int[][] mapWins 		= new int[numBots][numMaps];
	private int[][] mapGames 		= new int[numBots][numMaps];
	
	private int eloK				= 32;
	private double[] elo			= new double[numBots];
	private int[] timeout 			= new int[numBots];
	private int[] games 			= new int[numBots];
	private int[] crash 			= new int[numBots];
	private int[] frames 			= new int[numBots];
	private int[] mapUsage 			= new int[numMaps];
	private int[] hour 		        = new int[numBots];
	
	//first index is bot index, second index is round number
	private Vector<Vector<Integer>> winsAfterRound = new Vector<Vector<Integer>>();
	private Vector<Vector<Integer>> gamesAfterRound = new Vector<Vector<Integer>>();
	
	public ResultsParser(String filename)
	{
		// set the bot names and map names
		for (int i=0; i<botNames.length; ++i)
		{
			elo[i] = 1200;
			botNames[i] = ServerSettings.Instance().BotVector.get(i).getName();
			
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
		
	public void parseResults(Vector<GameResult> results)
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
			if (result.finalFrame <= 0 || !result.complete)
			{
				continue;
			}
			
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
				mapGames[b1][map]++;
				
				//record all results for combinations of bots in the game
				for (int k = j + 1; k < result.bots.size(); k++)
				{
					int b2 = getIndex(result.bots.get(k));
					wins[b1][b2] += (result.winner == i) ? 1 : 0;
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
		int[] allwins = new int[botNames.length];
		
		for (int i=0; i<numBots; i++)
		{
			for (int j=0; j<numBots; j++)
			{
				allwins[i] += wins[i][j];
			}
		}
		
		Vector<ResultPair> allPairs = new Vector<ResultPair>();
		for (int i=0; i<numBots; i++)
		{
			double winPercentage = (games[i] > 0 ? ((double)allwins[i]/games[i]) : 0);
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
		FileUtils.writeToFile(winPercentage.toString(), "html/results/winpercentage.js");
		FileUtils.writeToFile(roundWins.toString(), "html/results/roundwins.js");
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
		FileUtils.writeToFile(out.toString(), "html/results/detailed_results_json.js");
		FileUtils.writeToFile(outtxt.toString(), "html/results/detailed_results.txt");
	}
	
	public String getResultsJSON()
	{	
		int[] allgames = new int[botNames.length];
		int[] allwins = new int[botNames.length];
		
		for (int i=0; i<numBots; i++)
		{
			for (int j=0; j<numBots; j++)
			{
				allwins[i] += wins[i][j];
				allgames[i] += wins[i][j] + wins[j][i];
			}
		}
		
		Vector<ResultPair> allPairs = new Vector<ResultPair>();
		for (int i=0; i<numBots; i++)
		{
			double winPercentage = (allgames[i] > 0 ? ((double)allwins[i]/allgames[i]) : 0);
			allPairs.add(new ResultPair(botNames[i], i, winPercentage));
		}
		Collections.sort(allPairs, new ResultPairComparator());
		
		StringBuilder json = new StringBuilder();
		
		File resultsTXT = new File("html/results/detailed_results.txt");
		json.append("var resultsTXTSize = " + (resultsTXT.exists() ? resultsTXT.length()/1000 : 0) + ";\n");
		json.append("var lastUpdateTime = '" + new SimpleDateFormat("yyyy-MM-dd [HH:mm:ss]").format(Calendar.getInstance().getTime()) + "';\n");
		json.append("var maps = [");
		for (int i=0; i<numMaps; i++)
		{
			json.append("'" + mapNames[i].substring(mapNames[i].indexOf(')') + 1, mapNames[i].indexOf('.')) + "'");
			if (i != numMaps - 1)
			{
				json.append(",");
			}
		}
		json.append("]\n");
		
		json.append("var resultsSummary = [\n");
		
		for (int i=0; i<numBots; ++i)
		{
			int ii = allPairs.get(i).botIndex;
			
			json.append("\t{\"BotName\": \"" + botNames[ii] + "\", ");
			json.append("\"Rank\": " + i + ", ");
			json.append("\"Race\": \"" + ServerSettings.Instance().BotVector.get(ii).getRace() + "\", ");
			
			//parse BWAPI version to go from "BWAPI_412" -> "4.1.2"
			String version = ServerSettings.Instance().BotVector.get(ii).getBWAPIVersion();
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
			json.append("\"BWAPIVersion\": \"" + versionNum + "\", ");
			
			json.append("\"BotType\": \"" + ServerSettings.Instance().BotVector.get(ii).getType() + "\", ");
			json.append("\"Games\": " + allgames[ii] + ", ");
			json.append("\"Wins\": " + allwins[ii] + ", ");
			json.append("\"Losses\": " + (allgames[ii] - allwins[ii]) + ", ");
			json.append("\"Score\": " + new DecimalFormat("##.##").format(allPairs.get(i).win*100) + ", ");
			json.append("\"ELO\": " + (int)elo[ii] + ", "); 
			json.append("\"AvgTime\": " + (allgames[ii] > 0 ? frames[ii]/games[ii] : "0") + ", ");
			json.append("\"Hour\": " + hour[ii] + ", ");
			json.append("\"Crash\": " + crash[ii] + ", ");
			json.append("\"Timeout\": " + timeout[ii] + ", ");
			
			json.append("\"resultPairs\": [");
			
			for (int j=0; j<numBots; j++)
			{
				int jj = allPairs.get(j).botIndex;
				
				json.append("{\"Opponent\": \"" + botNames[jj] + "\", ");
				json.append("\"Wins\": " + wins[ii][jj] + ", ");
				json.append("\"Games\": " + (wins[ii][jj] + wins[jj][ii]) + "}");
				
				if (j != numBots - 1)
				{
					json.append(", ");
				}
			}
			json.append("], ");
			
			json.append("\"mapResults\": [");
			
			for (int j=0; j<numMaps; j++)
			{
				json.append("{\"Map\": \"" + mapNames[j].substring(mapNames[j].indexOf(')') + 1, mapNames[j].indexOf('.')) + "\", ");
				json.append("\"Wins\": " + mapWins[ii][j] + ", ");
				json.append("\"Games\": " + mapGames[ii][j] + "}");
				
				if (j != numMaps - 1)
				{
					json.append(", ");
				}
			}
			
			json.append("]}");
			
			if (i != numBots - 1)
			{
				json.append(", ");
			}
			
			json.append("\n");
		}
		
		json.append("];");
		
		return json.toString();
	}
		
	public int getIndex(String botName)
	{
		for (int i=0; i<botNames.length; i++)
		{
			if (botNames[i].equals(botName))
			{
				return i;
			}
		}
		return -1;
	}
	
	public int getMapIndex(String mapName)
	{
		for (int i=0; i<mapNames.length; i++)
		{
			if (mapNames[i].equals(mapName))
			{
				return i;
			}
		}
		return -1;
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
				if (excludedBot.equals(result.get("reportingBot").asString()))
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