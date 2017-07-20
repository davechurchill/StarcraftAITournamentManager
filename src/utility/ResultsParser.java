package utility;

import java.util.*;
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
	
	//private String[] botColors		= new String[numBots];
	private String[] botNames 		= new String[numBots];
	private String[] shortBotNames 	= new String[numBots];
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
	
	private Vector<Vector<Integer>> winsAfterRound = new Vector<Vector<Integer>>();
	private Vector<Vector<Integer>> gamesAfterRound = new Vector<Vector<Integer>>();
	
	public ResultsParser(String filename)
	{
		
		// set the bot names and map names
		for (int i=0; i<botNames.length; ++i)
		{
			elo[i] = 1200;
			botNames[i] = ServerSettings.Instance().BotVector.get(i).getName();
			shortBotNames[i] = botNames[i].substring(0, Math.min(4, botNames[i].length()));
			//botColors[i] = raceColor.get(ServerSettings.Instance().BotVector.get(i).getRace());
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
			if (result.finalFrame <= 0)
			{
				continue;
			}
			
			int b1 = getIndex(result.hostName);
			int b2 = getIndex(result.awayName);
			
			if (getIndex(result.timeOutName) != -1)
			{
				timeout[getIndex(result.timeOutName)]++;
			}
			
			if (getIndex(result.crashName) != -1)
			{
				crash[getIndex(result.crashName)]++;
			}
			
			hour[b1] += (result.hourTimeout) ? 1 : 0;
			hour[b2] += (result.hourTimeout) ? 1 : 0;
			
			games[b1]++;
			games[b2]++;
			
			frames[b1] += result.finalFrame;
			frames[b2] += result.finalFrame;
			
			int winner = getIndex(result.winName);
			int map = getMapIndex(result.mapName);
			
			wins[b1][b2] += (winner == b1) ? 1 : 0;
			wins[b2][b1] += (winner == b2) ? 1 : 0;
			
			mapUsage[map]++;
			mapWins[winner][map]++;
			mapGames[b1][map]++;
			mapGames[b2][map]++;
			
			// update elo
			updateElo(b1, b2, winner == b1);
						
			// update win percentage arrays
			if (result.roundID >= gamesAfterRound.get(b1).size())
			{
				gamesAfterRound.get(b1).add(gamesAfterRound.get(b1).lastElement() + 1);
			}
			else
			{
				gamesAfterRound.get(b1).set(result.roundID, gamesAfterRound.get(b1).get(result.roundID) + 1);
			}
			
			// if it's a new round, add it to the end of the vector
			if (result.roundID >= gamesAfterRound.get(b2).size())
			{
				gamesAfterRound.get(b2).add(gamesAfterRound.get(b2).lastElement() + 1);
			}
			// otherwise just add 1 to the back
			else
			{
				gamesAfterRound.get(b2).set(result.roundID, gamesAfterRound.get(b2).get(result.roundID) + 1);
			}
			
			while (result.roundID >= winsAfterRound.get(b1).size())
			{
				winsAfterRound.get(b1).add(winsAfterRound.get(b1).lastElement());		
			}
			
			while (result.roundID >= winsAfterRound.get(b2).size())
			{
				winsAfterRound.get(b2).add(winsAfterRound.get(b2).lastElement());		
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
		
		for (int i=0; i<numBots; ++i)
		{
			// only write the stats for this bot if it has completed at least one game
			if (games[i] > 0)
			{
				int ii = allPairs.get(i).botIndex;
				
				winPercentage.append("{ name: '" + botNames[ii] + "', data: [");
				roundWins.append("{ name: '" + botNames[ii] + "', data: [");
				for (int j=0; j<gamesAfterRound.get(ii).size(); ++j)
				{
					double winRate = (double)winsAfterRound.get(ii).get(j) / (double)gamesAfterRound.get(ii).get(j);
					
					winPercentage.append(" " + winRate);
					
					int wins = winsAfterRound.get(ii).get(j);
					if (j > 0)
					{
						wins -= winsAfterRound.get(ii).get(j-1);
					}
					
					roundWins.append(" " + wins);
					
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
		
		FileUtils.writeToFile(winPercentage.toString(), "html/winpercentage.txt");
		FileUtils.writeToFile(roundWins.toString(), "html/roundwins.txt");
	}
	
	public boolean hasGameResult(int gameID)
	{
		return gameIDs.contains(gameID);
	}
	
	public void writeDetailedResultsJSON()
	{
		StringBuilder out = new StringBuilder();
		StringBuilder outtxt = new StringBuilder();
		
		out.append("var detailedResults = [\n");
		
		for (int i=0; i<results.size(); i++)
		{				
			GameResult r = results.get(i);
			
			out.append("{");
			
			String idString = "" + r.gameID;
			while (idString.length() < 5) { idString = "0" + idString; }
			out.append("\"Round/Game\": \"" + r.roundID + " / " + idString + "\", ");
			
			String winnerReplayName = r.hostWon ? r.getHostReplayName() : r.getAwayReplayName();
			String loserReplayName = r.hostWon ? r.getAwayReplayName() : r.getHostReplayName();
			String winnerName = r.hostWon ? r.hostName : r.awayName;
			String loserName = r.hostWon ? r.awayName : r.hostName;
			
			out.append("\"WinnerName\": \"" + winnerName + "\", ");
			out.append("\"LoserName\": \"" + loserName + "\", ");
			
			if (new File("replays\\" + winnerReplayName).exists())
			{
				out.append("\"WinnerReplay\": \"" + winnerReplayName.replace("\\", "/") + "\", ");
			}
			else
			{
				out.append("\"WinnerReplay\": \"\", ");
			}
			
			if (new File("replays\\" + loserReplayName).exists())
			{
				out.append("\"LoserReplay\": \"" + loserReplayName.replace("\\", "/") + "\", ");
			}
			else
			{
				out.append("\"LoserReplay\": \"\", ");
			}
			
			out.append("\"Crash\": \"" + (r.finalFrame == -1 ? "unknown" : r.crashName) + "\", ");
			out.append("\"Timeout\": \"" + r.timeOutName + "\", ");
			out.append("\"Map\": \"" + r.mapName.substring(r.mapName.indexOf(')') + 1, r.mapName.indexOf('.')) + "\", ");
			
			String hours   = "" + r.finalFrame/(24*60*60); while (hours.length() < 2) { hours = "0" + hours; }
			String minutes = "" + (r.finalFrame % (24*60*60))/(24*60); while (minutes.length() < 2) { minutes = "0" + minutes; }
			String seconds = "" + (r.finalFrame % (24*60))/(24); while (seconds.length() < 2) { seconds = "0" + seconds; }
			
			out.append("\"Duration\": \"" + hours + ":" + minutes + ":" + seconds + "\", ");
			out.append("\"W Score\": " + (r.hostWon ? r.hostScore : r.awayScore) + ", ");
			out.append("\"L Score\": " + (r.hostWon ? r.awayScore : r.hostScore) + ", ");
			
			double maxScore = Math.max(r.hostScore, r.awayScore) + 1;
			double closeNess = (r.hostWon ?  (r.hostScore - r.awayScore)/(maxScore) : (r.awayScore - r.hostScore)/(maxScore));
			closeNess = (double)Math.round(closeNess * 100000) / 100000;
			out.append("\"(W-L)/Max\": " + closeNess + ", ");
			
			int numTimers = ServerSettings.Instance().tmSettings.TimeoutLimits.size();
			out.append("\"WinnerTimers\": [");
			for (int t=0; t<numTimers; ++t)
			{
				out.append("{\"Limit\": " + ServerSettings.Instance().tmSettings.TimeoutLimits.get(t) + ", \"Count\": " + (r.hostWon ? r.hostTimers.get(t) : r.awayTimers.get(t)) + "}");
				if (t != numTimers - 1)
				{
					out.append(", ");
				}
			}
			out.append("], ");
			
			out.append("\"LoserTimers\": [");
			for (int t=0; t<numTimers; ++t)
			{
				out.append("{\"Limit\": " + ServerSettings.Instance().tmSettings.TimeoutLimits.get(t) + ", \"Count\": " + (r.hostWon ? r.awayTimers.get(t) : r.hostTimers.get(t)) + "}");
				if (t != numTimers - 1)
				{
					out.append(", ");
				}
			}
			out.append("], ");
			
			out.append("\"Win Addr\": \"" + (r.hostWon ? r.hostAddress : r.awayAddress) + "\", ");
			out.append("\"Lose Addr\": \"" + (r.hostWon ? r.awayAddress : r.hostAddress) + "\", ");
			out.append("\"Start\": \"" + (r.startDate) + "\", ");
			out.append("\"Finish\": \"" + (r.finishDate) + "\"}");
			
			if (i != results.size() - 1)
			{
				out.append(",");
			}
			out.append("\n");
			
			outtxt.append(r.getResultString() + "\n");
		}
		
		out.append("]");
		
		FileUtils.writeToFile(out.toString(), "html/detailed_results_json.txt");
		FileUtils.writeToFile(outtxt.toString(), "html/detailed_results.txt");
	}
	
	public void writeDetailedResultsHTML()
	{
		System.out.println("Writing detailed results html");
		
		StringBuilder out = new StringBuilder();
		StringBuilder outtxt = new StringBuilder();
		
		int numTimers = ServerSettings.Instance().tmSettings.TimeoutLimits.size();
		out.append("<!DOCTYPE html>\n");
		out.append("<html>\n<head>\n");
		out.append("<title>Detailed Results</title>\n");
		out.append("<meta charset=\"UTF-8\">\n");
		out.append("<script src=\"javascript/jquery-1.10.2.min.js\"></script>\n<script src=\"javascript/jquery.tablesorter.js\"></script>\n");
		out.append("<link rel=\"stylesheet\" href=\"javascript/themes/blue/style.css\">\n");
		out.append("<link rel=\"stylesheet\" href=\"css/style.css\">\n");
		out.append("<script> $(function() { $(\"#detailedResultsTable\").tablesorter(); });</script>\n");
		out.append("</head>\n");
		out.append("<p>Go To: <a href=\"index.html\">Results Overview</a>. Click column header to sort. Sort multiple columns by holding shift.</p>\n");
		out.append("<table id=\"detailedResultsTable\" class=\"tablesorter\">\n");
		out.append("  <thead><tr>\n");
		out.append("    <th>Round/Game</th>\n");
		out.append("    <th>Winner</th>\n");
		out.append("    <th>Loser</th>\n");
		out.append("    <th>Crash</th>\n");
		out.append("    <th>Timeout</th>\n");
		out.append("    <th>Map</th>\n");
		out.append("    <th>Duration</th>\n");
		out.append("    <th>W Score</th>\n");
		out.append("    <th>L Score</th>\n");
		out.append("    <th>(W-L)/Max</th>\n");
		for (int t=0; t<numTimers; ++t)
		{
			out.append("    <th>W " + ServerSettings.Instance().tmSettings.TimeoutLimits.get(t) + "</th>\n");
		}
		for (int t=0; t<numTimers; ++t)
		{
			out.append("    <th>L " + ServerSettings.Instance().tmSettings.TimeoutLimits.get(t) + "</th>\n");
		}
		out.append("    <th>Win Addr</th>\n");
		out.append("    <th>Lose Addr</th>\n");
		out.append("    <th>Start</th>\n");
		out.append("    <th>Finish</th>\n");
		out.append("  </tr></thead><tbody>\n");
				
		//Collections.sort(results, new GameResultIDComparator());
		
		for (int i=0; i<results.size(); i++)
		{				
			GameResult r = results.get(i);
			out.append("  <tr>\n");
			
			String idString = "" + r.gameID;
			while (idString.length() < 5) { idString = "0" + idString; }
			
			out.append("    <td>" + ( r.roundID + " / " + idString) + "</td>\n");
			
			String winnerReplayName = r.hostWon ? r.getHostReplayName() : r.getAwayReplayName();
			String loserReplayName = r.hostWon ? r.getAwayReplayName() : r.getHostReplayName();
			String winnerName = r.hostWon ? r.hostName : r.awayName;
			String loserName = r.hostWon ? r.awayName : r.hostName;
			
			if (new File("replays\\" + winnerReplayName).exists())
			{
				out.append("    <td>" + "<a href=\"replays/" + winnerReplayName.replace("\\", "/") + "\">" + winnerName + "</a></td>\n");
			}
			else
			{
				out.append("    <td>" + winnerName + "</td>\n");
			}
			
			if (new File("replays\\" + loserReplayName).exists())
			{
				out.append("    <td>" + "<a href=\"replays/" + loserReplayName.replace("\\", "/") + "\">" + loserName + "</a></td>\n");
			}
			else
			{
				out.append("    <td>" + loserName + "</td>\n");
			}
			
			out.append("    <td>" + (r.finalFrame == -1 ? "unknown" : r.crashName) + "</td>\n");
			out.append("    <td>" + r.timeOutName + "</td>\n");
			out.append("    <td>" + r.mapName.substring(r.mapName.indexOf(')') + 1, r.mapName.indexOf('.')) + "</td>\n");
			
			String hours   = "" + r.finalFrame/(24*60*60); while (hours.length() < 2) { hours = "0" + hours; }
			String minutes = "" + (r.finalFrame % (24*60*60))/(24*60); while (minutes.length() < 2) { minutes = "0" + minutes; }
			String seconds = "" + (r.finalFrame % (24*60))/(24); while (seconds.length() < 2) { seconds = "0" + seconds; }
			
			out.append("    <td>00:" + minutes + ":" + seconds + "</td>\n");
			out.append("    <td>" + (r.hostWon ? r.hostScore : r.awayScore) + "</td>\n");
			out.append("    <td>" + (r.hostWon ? r.awayScore : r.hostScore) + "</td>\n");
			
			double maxScore = Math.max(r.hostScore, r.awayScore) + 1;
			double maxSq = (double)maxScore;// * (double)maxScore;
			double hostSq = (double)r.hostScore;// * (double)r.hostScore;
			double awaySq = (double)r.awayScore;// * (double)r.awayScore;
			double closeNess = (r.hostWon ?  (hostSq-awaySq)/(maxSq) : (awaySq-hostSq)/(maxSq));
			closeNess = (double)Math.round(closeNess * 100000) / 100000;
			out.append("    <td>" + closeNess + "</td>\n");
			
			for (int t=0; t<numTimers; ++t)
			{
				out.append("    <td>" + (r.hostWon ? r.hostTimers.get(t) : r.awayTimers.get(t)) + "</td>\n");
			}
			
			for (int t=0; t<numTimers; ++t)
			{
				out.append("    <td>" + (r.hostWon ? r.awayTimers.get(t) : r.hostTimers.get(t)) + "</td>\n");
			}
			
			out.append("    <td>" + (r.hostWon ? r.hostAddress : r.awayAddress) + "</td>\n");
			out.append("    <td>" + (r.hostWon ? r.awayAddress : r.hostAddress) + "</td>\n");
			out.append("    <td>" + (r.startDate) + "</td>\n");
			out.append("    <td>" + (r.finishDate) + "</td>\n");
			out.append("  </tr>\n");
			
			outtxt.append(r.getResultString() + "\n");
		}
		
		out.append("</tbody></table>\n");
		out.append("</html>\n");
		
		FileUtils.writeToFile(out.toString(), "html/results.html");
		FileUtils.writeToFile(outtxt.toString(), "html/detailed_results.txt");
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
		
		File resultsTXT = new File("html/detailed_results.txt");
		json.append("var resultsTXTSize = " + (resultsTXT.exists() ? resultsTXT.length() : 0) + ";\n");
		json.append("var lastUpdateTime = '" + new SimpleDateFormat("yyyy-MM-dd [HH:mm:ss]").format(Calendar.getInstance().getTime()) + "';\n");
		json.append("var maps = [");
		for (int i=0; i<numMaps; i++)
		{
			json.append("'" + mapNames[i] + "'");
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
				json.append("{\"Map\": \"" + mapNames[j] + "\", ");
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
	
	public String getHeaderHTML()
	{
		String html = "<!DOCTYPE html>\n";
		html += "<html>\n";
		html += "<head>\n";
		html += "<title>StarCraft AI Competition Results</title>\n";
		html += "<meta charset=\"UTF-8\">";
		html += "<script src=\"javascript/jquery-1.10.2.min.js\"></script>\n<script src=\"javascript/jquery.tablesorter.js\"></script>\n";
		html += "<link rel=\"stylesheet\" href=\"javascript/themes/blue/style.css\">\n";
		html += "<link rel=\"stylesheet\" href=\"css/style.css\">\n";
		html += "<script> $(function() { $(\"#resultsTable\").tablesorter(); });</script>\n";
		html += "<script> $(function() { $(\"#resultsPairTable\").tablesorter(); });</script>\n";
		html += "<script> $(function() { $(\"#resultsMapTable\").tablesorter(); });</script>\n";
		html += "</head>\n";
		
		return html;
	}
	
	public String getFooterHTML()
	{
		String timeStamp = "<p id=\"footer\">Last Updated: " + new SimpleDateFormat("yyyy-MM-dd [HH:mm:ss]").format(Calendar.getInstance().getTime()) + "</p>\n";
		String html = timeStamp;
		html += "</body>\n";
		html += "</html>\n";
		return html;
	}
	
	public String getResultsHTML()
	{	
		
		String html = "";
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
		
		File resultsHTML = new File("html/results.html");
		long resultsHTMLSize = resultsHTML.exists() ? resultsHTML.length() : 0;
		File resultsTXT = new File("html/detailed_results.txt");
		long resultsTXTSize = resultsTXT.exists() ? resultsTXT.length() : 0;
		html += "<p>Win Percentage Over Time Graph: <a href=\"win_percentage_graph.html\">html5</a> </p>\n";
		html += "<p>Detailed Game Results: <a href=\"results.html\">html table</a> (" + resultsHTMLSize/1000  + " kB) or <a href=\"detailed_results.txt\">plaintext</a> (" + resultsTXTSize/1000  + " kB)</p>\n";
		
		
		/////////////////////////////////////////
		// Results Table
		/////////////////////////////////////////
		html += "<table id=\"resultsTable\" class=\"tablesorter\">\n";
		html += "  <thead><tr>\n";
		html += "    <th>Bot</th>\n";
		html += "    <th>Games</th>\n";
		html += "    <th>Win</th>\n";
		html += "    <th>Loss</th>\n";
		html += "    <th>Win %</th>\n";
		//html += "    <th>Elo (K=\" + eloK + \")</th>\n";
		html += "    <th>AvgTime</th>\n";
		html += "    <th>Hour</th>\n";
		html += "    <th>Crash</th>\n";
		html += "    <th>Timeout</th>\n";
		html += "  </tr></thead>\n";
		
		int[] dataTotals = {0, 0, 0, 0, 0, 0, 0, 0};
		
		for (int i=0; i<numBots; ++i)
		{
			int ii = allPairs.get(i).botIndex;
			
			html += "  <tr>\n";
			html += "    <td class=\"race-" + ServerSettings.Instance().BotVector.get(ii).getRace().toLowerCase() + "\">"+ botNames[ii] + "</td>\n"; 
			html += "    <td >" + allgames[ii] + "</td>\n";
			dataTotals[0] += allgames[ii];			
			html += "    <td>" + allwins[ii] + "</td>\n";
			dataTotals[1] += allwins[ii];
			html += "    <td>" + (allgames[ii] - allwins[ii]) + "</td>\n";
			dataTotals[2] += (allgames[ii] - allwins[ii]);			
			html += "    <td>" + new DecimalFormat("##.##").format(allPairs.get(i).win*100) + "</td>\n";
			//html += "    <td>"+ (int)elo[ii] + "</td>\n"; 
			html += "    <td>" + (allgames[ii] > 0 ? getTime(frames[ii]/games[ii]) : "0") + "</td>\n";
			dataTotals[4] += (allgames[ii] > 0 ? frames[ii]/games[ii] : 0);
			html += "    <td>" + hour[ii] + "</td>\n";
			dataTotals[5] += hour[ii];
			html += "    <td>" + crash[ii] + "</td>\n"; 
			dataTotals[6] += crash[ii];			
			html += "    <td>" + timeout[ii] + "</td>\n";	
			dataTotals[7] += timeout[ii];
			html += "  </tr>\n";
		}
		
		html += "  <tfoot><tr>\n";
		html += "    <td><b>Total</b></td>\n";
		html += "    <td>" + (dataTotals[0]/2) + "</td>\n";
		html += "    <td>" + (dataTotals[1]) + "</td>\n";
		html += "    <td>" + (dataTotals[2]) + "</td>\n";
		html += "    <td>" + "N/A" + "</td>\n";
		html += "    <td>" + getTime((dataTotals[4]/botNames.length)) + "</td>\n";
		html += "    <td>" + (dataTotals[5]/2) + "</td>\n";
		html += "    <td>" + (dataTotals[6]) + "</td>\n";
		html += "    <td>" + (dataTotals[7]) + "</td>\n";
		html += "  </tr></tfoot>\n";
		
		html += "</table>\n";
		
		/////////////////////////////////////////
		// Result Pairs Table
		/////////////////////////////////////////
		html += "<table id=\"resultsPairTable\" class=\"tablesorter\">\n";
		html += "  <thead><tr>\n";
		html += "    <th>Bot</th>\n";
		html += "    <th>Win %</th>\n";
		
		for (int i=0; i<numBots; ++i)
		{
			int ii = allPairs.get(i).botIndex;
			html += "    <th class=\"race-" + ServerSettings.Instance().BotVector.get(ii).getRace().toLowerCase() + "\">" + shortBotNames[ii] + "</th>\n";
		}
		html += "  </tr></thead>\n";
		
		for (int i=0; i<numBots; i++)
		{
			int ii = allPairs.get(i).botIndex;
			html += "  <tr>\n";
			
			html += "    <td class=\"race-" + ServerSettings.Instance().BotVector.get(ii).getRace().toLowerCase() + "\">" + botNames[ii] + "</td>\n";
			html += "    <td>" + new DecimalFormat("##.##").format(allPairs.get(i).win*100) + "</td>\n";
			
			
			for (int j=0; j<numBots; j++)
			{
				int jj = allPairs.get(j).botIndex;
				if (ii == jj)
				{
					html += "    <td style=\"background-color: #ffffff\">-</td>\n";
				}
				else
				{
					double w = wins[ii][jj];
					double g = wins[ii][jj] + wins[jj][ii];
					double l = g - w;
					double p = g > 0 ? w / g : 0;
					int c = (int)(p * 255);
												
					String cellColor = "rgb(" + (255-c) + "," + 255 + "," + (255-c/2) + ")";
					
					if (w >= l)
					{
						c = 255 - c;
						c = (int)(1.7*c);
						cellColor = "rgb(" + (c) + "," + 255 + "," + (c) + ")";
					}
					
					if (l > w)
					{
						c = (int)(1.7*c);
						cellColor = "rgb(" + (255) + "," + (c) + "," + (c) + ")";
					}
					
					html += "    <td style=\"background-color:" + cellColor + "\">" + String.format("%02d", wins[ii][jj]) + "/" + String.format("%02d", (int)g) + "</td>\n";
				}
			}
			html += "  </tr>\n";
		}
		
		html += "</table>\n";		
			
		/////////////////////////////////////////
		// MAP WINS TABLE
		/////////////////////////////////////////
		html += "<table id=\"resultsMapTable\" class=\"tablesorter\">\n";
		html += "  <thead><tr>\n";
		
		html += "    <th>Bot</th>\n";
		for (int i=0; i<numMaps; ++i)
		{
			html += "    <th>" + mapNames[i].substring(3, Math.min(10, mapNames[i].length()-4)) + "</th>\n";
		}
		
		html += "  </tr></thead>\n";
		
		for (int i=0; i<numBots; i++)
		{
			int ii = allPairs.get(i).botIndex;
			html += "  <tr>\n";
			html += "    <td class=\"race-" + ServerSettings.Instance().BotVector.get(ii).getRace().toLowerCase() + "\">" + botNames[ii] + "</td>\n";
			
			
			for (int j=0; j<numMaps; j++)
			{
				double w = mapWins[ii][j];
				double g = mapGames[ii][j];
				double l = g - w;
				double p = g > 0 ? w / g : 0;
				int c = (int)(p * 255);

				String cellColor = "rgb(" + (255-c) + "," + 255 + "," + (255-c) + ")";
				
				if (w >= l)
				{
					c = 255 - c;
					c = (int)(1.7*c);
					cellColor = "rgb(" + (c) + "," + 255 + "," + (c) + ")";
				}
				
				if (l > w)
				{
					c = (int)(1.7*c);
					cellColor = "rgb(" + (255) + "," + (c) + "," + (c) + ")";
				}
				
				html += "    <td style=\"background-color:" + cellColor + "\">" + String.format("%03d", mapWins[ii][j]) + "/" + String.format("%03d", mapGames[ii][j]) + "</td>\n";
				
			}
			html += "  </tr>\n";
		}
		
		
		html += "</table>\n";
		
		return html;
	}
	
	public String getTime(int frames)
	{
		int fpm = 24*60;
		int fps = 24;
		int minutes = frames / fpm;
		int seconds = (frames / fps) % 60;
		
		return "" + minutes + ":" + (seconds < 10 ? "0" + seconds : seconds);
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
			Vector<String> data = new Vector<String>(Arrays.asList(line.trim().split(" +")));
					
			int gameID = Integer.parseInt(data.get(0));
			gameIDs.add(gameID);
			
			if (gameResults.containsKey(gameID))
			{
				gameResults.get(gameID).setResult(line);
			}
			else
			{
				gameResults.put(gameID, new GameResult(line));
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
		return o1.crashName.compareTo(o2.crashName);
	}
}

class GameResultTimeOutComparator implements Comparator<GameResult>
{
    public int compare(GameResult o1, GameResult o2)
    {
		return o1.timeOutName.compareTo(o2.timeOutName);
	}
}

class GameWinnerNameComparator implements Comparator<GameResult>
{
    public int compare(GameResult o1, GameResult o2)
    {
		return o1.winName.compareTo(o2.winName);
	}
}