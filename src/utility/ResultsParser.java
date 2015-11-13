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
	Map<String, String> raceColor   = new HashMap<String, String>();
	
	private int numBots 			= ServerSettings.Instance().BotVector.size();
	private int numMaps 			= ServerSettings.Instance().MapVector.size();
	
	private String[] botColors		= new String[numBots];
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
		raceColor.put("Protoss", "#f1c232");
		raceColor.put("Zerg", "#c27ba0");
		raceColor.put("Terran", "#6fa8dc");
		raceColor.put("Random", "#cccccc");
		
		// set the bot names and map names
		for (int i=0; i<botNames.length; ++i)
		{
			elo[i] = 1200;
			botNames[i] = ServerSettings.Instance().BotVector.get(i).getName();
			shortBotNames[i] = botNames[i].substring(0, Math.min(4, botNames[i].length()));
			botColors[i] = raceColor.get(ServerSettings.Instance().BotVector.get(i).getRace());
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
		
		printWinPercentageGraph();
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
	
	public String getRawDataHTML()
	{
		String data = "";
		for (int i=0; i<results.size(); i++)
		{
			data += results.get(i).toString();
		}
		
		return data;
	}
	
	public void printWinPercentageGraph()
	{
		try
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
			
			BufferedWriter out = new BufferedWriter(new FileWriter("html/winpercentage.txt"));
			BufferedWriter out2 = new BufferedWriter(new FileWriter("html/roundwins.txt"));
			
			String s = "";
			for (int i=0; i<numBots; ++i)
			{
				int ii = allPairs.get(i).botIndex;
				
				out.write("{ name: '" + botNames[ii] + "', data: [");
				out2.write("{ name: '" + botNames[ii] + "', data: [");
				for (int j=0; j<gamesAfterRound.get(ii).size(); ++j)
				{
					double winRate = (double)winsAfterRound.get(ii).get(j) / (double)gamesAfterRound.get(ii).get(j);
					
					out.write(" " + winRate);
					
					int wins = winsAfterRound.get(ii).get(j);
					if (j > 0)
					{
						wins -= winsAfterRound.get(ii).get(j-1);
					}
					
					out2.write(" " + wins);
					
					if (j < gamesAfterRound.get(ii).size() - 1)
					{
						out.write(",");
						out2.write(",");
					}
				}
				out.write("] }");
				out2.write("] }");
				
				if (i < numBots - 1)
				{
					out.write(",");
					out2.write(",");
				}
				
				out.write("\n");
				out2.write("\n");
			}
		
			out.close();
			out2.close();
		}
		catch (Exception e)
		{
			
		}
	}
	
	public boolean hasGameResult(int gameID)
	{
		return gameIDs.contains(gameID);
	}
	
	public void writeDetailedResultsHTML(String filename)
	{
		try
		{
			System.out.println("Writing detailed results html");
			BufferedWriter out = new BufferedWriter(new FileWriter(filename));
			BufferedWriter outtxt = new BufferedWriter(new FileWriter("html/detailed_results.txt"));
			
			int numTimers = ServerSettings.Instance().tmSettings.TimeoutLimits.size();
			int width = 89;
			out.write("<html><head>\n");
			out.write("<script type=\"text/javascript\" src=\"javascript/jquery-1.10.2.min.js\"></script>	<script type=\"text/javascript\" src=\"javascript/jquery.tablesorter.js\"></script> <style> td { text-align:center; } </style>\n");
			out.write("<link rel=\"stylesheet\" href=\"javascript/themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n");
			out.write("<script type=\"text/javascript\"> $(function() { $(\"#resultsTable\").tablesorter({widgets: ['zebra']}); });</script>\n");
			out.write("</head>\n"); 
			out.write("<p style=\"font: 16px/1.5em Verdana\">Go To: <a href=\"index.html\">Results Overview</a>. Click column header to sort. Sort multiple columns by holding shift.</p>\n");
			out.write("<table cellpadding=2 rules=all style=\"font: 10px/1.5em Verdana\" id=\"resultsTable\" class=\"tablesorter\">\n");
			out.write("  <thead><tr>\n");
			out.write("    <th width=" + 130 + ">Round/Game</td>\n");
			out.write("    <th width=" + 100 + ">Winner</td>\n");
			out.write("    <th width=" + 100 + ">Loser</td>\n");
			out.write("    <th width=" + 100 + ">Crash</td>\n");
			out.write("    <th width=" + 100 + ">Timeout</td>\n");
			out.write("    <th width=" + 100 + ">Map</td>\n");
			out.write("    <th width=" + 100 + ">Duration</td>\n");
			out.write("    <th width=" + 100 + ">W Score</td>\n");
			out.write("    <th width=" + 100 + ">L Score</td>\n");
			out.write("    <th width=" + 100 + ">(W-L)/Max</td>\n");
			for (int t=0; t<numTimers; ++t)
			{
				out.write("    <th width=" + width + ">W " + ServerSettings.Instance().tmSettings.TimeoutLimits.get(t) + "</td>\n");
			}
			for (int t=0; t<numTimers; ++t)
			{
				out.write("    <th width=" + width + ">L " + ServerSettings.Instance().tmSettings.TimeoutLimits.get(t) + "</td>\n");
			}
			out.write("    <th width=" + 100 + ">Win Addr</td>\n");
			out.write("    <th width=" + 100 + ">Lose Addr</td>\n");
			out.write("    <th width=" + 100 + ">Start</td>\n");
			out.write("    <th width=" + 100 + ">Finish</td>\n");
			out.write("  </tr></thead><tbody>\n");
					
			//Collections.sort(results, new GameResultIDComparator());
			
			for (int i=0; i<results.size(); i++)
			{				
				GameResult r = results.get(i);
				out.write("  <tr>\n");
				
				String idString = "" + r.gameID;
				while (idString.length() < 5) { idString = "0" + idString; }
				
				out.write("    <td>" + ( r.roundID + " / " + idString) + "</td>\n");
				
				String winnerReplayName = r.hostWon ? r.getHostReplayName() : r.getAwayReplayName();
				String loserReplayName = r.hostWon ? r.getAwayReplayName() : r.getHostReplayName();
				String winnerName = r.hostWon ? r.hostName : r.awayName;
				String loserName = r.hostWon ? r.awayName : r.hostName;
				
				if (new File("replays\\" + winnerReplayName).exists())
				{
					out.write("    <td>" + "<a href=\"replays/" + winnerReplayName + "\">" + winnerName + "</a></td>\n");
				}
				else
				{
					out.write("    <td>" + winnerName + "</td>\n");
				}
				
				if (new File("replays\\" + loserReplayName).exists())
				{
					out.write("    <td>" + "<a href=\"replays/" + loserReplayName + "\">" + loserName + "</a></td>\n");
				}
				else
				{
					out.write("    <td>" + loserName + "</td>\n");
				}
				
				out.write("    <td>" + r.crashName + "</td>\n");
				out.write("    <td>" + r.timeOutName + "</td>\n");
				out.write("    <td>" + r.mapName.substring(r.mapName.indexOf(')') + 1, r.mapName.indexOf('.')) + "</td>\n");
				
				String hours   = "" + r.finalFrame/(24*60*60); while (hours.length() < 2) { hours = "0" + hours; }
				String minutes = "" + (r.finalFrame % (24*60*60))/(24*60); while (minutes.length() < 2) { minutes = "0" + minutes; }
				String seconds = "" + (r.finalFrame % (24*60))/(24); while (seconds.length() < 2) { seconds = "0" + seconds; }
				
				out.write("    <td>00:" + minutes + ":" + seconds + "</td>\n");
				out.write("    <td>" + (r.hostWon ? r.hostScore : r.awayScore) + "</td>\n");
				out.write("    <td>" + (r.hostWon ? r.awayScore : r.hostScore) + "</td>\n");
				
				double maxScore = Math.max(r.hostScore, r.awayScore) + 1;
				double maxSq = (double)maxScore;// * (double)maxScore;
				double hostSq = (double)r.hostScore;// * (double)r.hostScore;
				double awaySq = (double)r.awayScore;// * (double)r.awayScore;
				double closeNess = (r.hostWon ?  (hostSq-awaySq)/(maxSq) : (awaySq-hostSq)/(maxSq));
				closeNess = (double)Math.round(closeNess * 100000) / 100000;
				out.write("    <td>" + closeNess + "</td>\n");
				
				for (int t=0; t<numTimers; ++t)
				{
					out.write("    <td>" + (r.hostWon ? r.hostTimers.get(t) : r.awayTimers.get(t)) + "</td>\n");
				}
				
				for (int t=0; t<numTimers; ++t)
				{
					out.write("    <td>" + (r.hostWon ? r.awayTimers.get(t) : r.hostTimers.get(t)) + "</td>\n");
				}
				
				out.write("    <td>" + (r.hostWon ? r.hostAddress : r.awayAddress) + "</td>\n");
				out.write("    <td>" + (r.hostWon ? r.awayAddress : r.hostAddress) + "</td>\n");
				out.write("    <td>" + (r.startDate) + "</td>\n");
				out.write("    <td>" + (r.finishDate) + "</td>\n");
				out.write("  </tr>\n");
				
				outtxt.write(r.getResultString());
				outtxt.write("\n");
			}
			
			out.write("</tbody></table>\n");
			out.write("<br></html>\n");
			out.close();
			outtxt.close();
		}
		catch (IOException e)
		{
			System.out.println(e.getStackTrace());			
		}
	}
	
	public String getHeaderHTML()
	{
		String html = "<html>\n";
		html += "<head>\n";
		html += "<title>StarCraft AI Competition Results</title>\n";
		html += "<script type=\"text/javascript\" src=\"javascript/jquery-1.10.2.min.js\"></script>	<script type=\"text/javascript\" src=\"javascript/jquery.tablesorter.js\"></script>\n";
		html += "<link rel=\"stylesheet\" href=\"javascript/themes/blue/style.css\" type=\"text/css\" media=\"print, projection, screen\" />\n";
		html += "<script type=\"text/javascript\"> $(function() { $(\"#resultsTable\").tablesorter({widgets: ['zebra']}); });</script>\n";
		html += "<script type=\"text/javascript\"> $(function() { $(\"#resultsPairTable\").tablesorter({widgets: ['zebra']}); });</script>\n";
		html += "<script type=\"text/javascript\"> $(function() { $(\"#resultsMapTable\").tablesorter({widgets: ['zebra']}); });</script>\n";
		html += "</head>\n";
		html += "<body alink=\"#0000FF\" vlink=\"#0000FF\" link=\"#0000FF\">\n";
		
		return html;
	}
	
	public String getEntrantsHTML()
	{
		String html = "";
	
		try
		{
			BufferedReader br = new BufferedReader(new FileReader("..\\html\\entrants.html"));
			String line;
			
			while ((line = br.readLine()) != null)
			{
				html += line + "\n";
			}
			html += "<br>\n";
			br.close();
			return html;
		}
		catch (Exception e)
		{
			return "";
		}
	}
	
	public String getFooterHTML()
	{
		String timeStamp = "<p style=\"font: 10px/1.5em Verdana\">Last Updated: " + new SimpleDateFormat("yyyy-MM-dd [HH:mm:ss]").format(Calendar.getInstance().getTime()) + "</p>\n";
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
		
		/////////////////////////////////////////
		// EXTRA STATS
		/////////////////////////////////////////
		int width = 80;
		File resultsHTML = new File("html/results.html");
		long resultsHTMLSize = resultsHTML.exists() ? resultsHTML.length() : 0;
		File resultsTXT = new File("html/detailed_results.txt");
		long resultsTXTSize = resultsTXT.exists() ? resultsTXT.length() : 0;
		html += "<p style=\"font: 16px/1.5em Verdana\">Detailed Game Results: <a href=\"results.html\">html table</a> (" + resultsHTMLSize/1000  + " kB) or <a href=\"detailed_results.txt\">plaintext</a> (" + resultsTXTSize/1000  + " kB)</p>\n";
		
		html += "<table cellpadding=2 rules=all style=\"font: 14px/1.5em Verdana\" id=\"resultsTable\" class=\"tablesorter\">\n";
		html += "  <thead><tr>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Bot</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Games</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Win</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Loss</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Win %</td>\n";
		//html += "    <td bgcolor=#CCCCCC align=center width=" + width + ">Elo (K=" + eloK + ")</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">AvgTime</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Hour</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Crash</td>\n";
		html += "    <th bgcolor=#CCCCCC align=center width=" + width + ">Timeout</td>\n";
		html += "  </tr></thead>\n";
		
		int[] dataTotals = {0, 0, 0, 0, 0, 0, 0, 0};
		
		for (int i=0; i<numBots; ++i)
		{
			int ii = allPairs.get(i).botIndex;
			String color = ((i%2) == 1 ? "#ffffff" : "#E8E8E8");
			
			html += "  <tr>\n";
			html += "    <td bgcolor=" + botColors[ii] + ">"+ botNames[ii] + "</td>\n"; 
			html += "    <td >" + allgames[ii] + "</td>\n";
			dataTotals[0] += allgames[ii];			
			html += "    <td>" + allwins[ii] + "</td>\n";
			dataTotals[1] += allwins[ii];
			html += "    <td>" + (allgames[ii] - allwins[ii]) + "</td>\n";
			dataTotals[2] += (allgames[ii] - allwins[ii]);			
			html += "    <td>" + new DecimalFormat("##.##").format(allPairs.get(i).win*100) + "</td>\n";
			//html += "    <td>"+ (int)elo[ii] + "</td>\n"; 
			html += "    <td>" + (allgames[ii] > 0 ? getTime(frames[ii]/games[ii]) : "0") + "</td>\n"; 	;
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
		html += "    <td align=center bgcolor=#CCCCCC><b>Total</b></td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + (dataTotals[0]/2) + "</td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + (dataTotals[1]) + "</td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + (dataTotals[2]) + "</td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + "N/A" + "</td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + getTime((dataTotals[4]/botNames.length)) + "</td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + (dataTotals[5]/2) + "</td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + (dataTotals[6]) + "</td>\n";
		html += "    <td align=center bgcolor=#CCCCCC>" + (dataTotals[7]) + "</td>\n";
		html += "  </tr></tfoot>\n";
		
		html += "</table>\n";
		html += "<br>\n";
		
		html += "<table cellpadding=2 rules=all style=\"font: 14px/1.5em Verdana\"  id=\"resultsPairTable\" class=\"tablesorter\">\n";
		html += "  <thead><tr>\n";
		html += "    <th width=100 align=center bgcolor=#CCCCCC>Bot</td>\n";
		html += "    <th width=71 align=center bgcolor=#CCCCCC>Win %</td>\n";
		
		for (int i=0; i<numBots; ++i)
		{
			int ii = allPairs.get(i).botIndex;
			html += "    <th width=50 align=left bgcolor=" + botColors[ii] + ">" + shortBotNames[ii] + "</td>\n";
		}
		html += "  </tr></thead>\n";
		
		for (int i=0; i<numBots; i++)
		{
			int ii = allPairs.get(i).botIndex;
			html += "  <tr>\n";
			
			String color = ((i%2) == 1 ? "#ffffff" : "#E8E8E8");
			html += "    <td width=100 align=center  bgcolor=" + botColors[ii] + ">" + botNames[ii] + "</td>\n";
			html += "    <td width=50 align=center bgcolor=" + color + ">" + new DecimalFormat("##.##").format(allPairs.get(i).win*100) + "</td>\n";
			
			
			for (int j=0; j<numBots; j++)
			{
				int jj = allPairs.get(j).botIndex;
				if (ii == jj)
				{
					html += "    <td align=center bgcolor=#ffffff>" + "-" + "</td>\n";
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
					
					html += "    <td align=center style=\"background-color:" + cellColor + "\">" + String.format("%02d", wins[ii][jj]) + "/" + String.format("%02d", (int)g) + "</td>\n";
				}
			}
			html += "  </tr>\n";
		}
		
		html += "</table>\n";		
		html += "<br>\n";
			
		/////////////////////////////////////////
		// MAP WINS TABLE
		/////////////////////////////////////////
		html += "<table cellpadding=2 rules=all style=\"font: 12px/1.5em Verdana\" id=\"resultsMapTable\" class=\"tablesorter\">\n";
		html += "  <thead><tr>\n";
		
		html += "    <th width=63 align=center bgcolor=#CCCCCC style=\"font: 11px/1.5em Verdana\">Bot</td>\n";
		for (int i=0; i<numMaps; ++i)
		{
			html += "    <th width=63 align=center bgcolor=#CCCCCC style=\"font: 11px/1.5em Verdana\">" + mapNames[i].substring(3, Math.min(10, mapNames[i].length()-4)) + "</td>\n";
		}
		
		html += "  </tr></thead>\n";
		
		for (int i=0; i<numBots; i++)
		{
			int ii = allPairs.get(i).botIndex;
			html += "  <tr>\n";
			
			html += "    <td width=100 align=center  bgcolor=" + botColors[ii] + ">" + botNames[ii] + "</td>\n";
			
			
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
				
				html += "    <td align=center style=\"background-color:" + cellColor + "\">" + String.format("%03d", mapWins[ii][j]) + "/" + String.format("%03d", mapGames[ii][j]) + "</td>\n";
				
			}
			html += "  </tr>\n";
		}
		
		
		html += "</table>\n";
		html += "<br>\n";
		
		
		
		return html;
	}
	
	public void writeToFile(String s, String filename) throws Exception
	{
		File file = new File(filename);
		if (!file.exists()) {
			file.createNewFile();
		}

		FileWriter fw = new FileWriter(file.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(s);
		bw.close();
		fw.close();
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
	
	public void parseLine(String line)
	{
		if (line.trim().length() > 0)
		{
			Vector<String> data = new Vector<String>(Arrays.asList(line.split(" +")));
			data.remove(0);
		
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