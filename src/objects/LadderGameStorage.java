package objects;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;

import server.Server;
import server.ServerSettings;
import utility.FileUtils;
import utility.GameParser;

public class LadderGameStorage extends GameStorage {
	
	private HashMap<Integer, Game> gamesInProgress;
	private TreeMap<Integer, Game> allGames;
	private Vector<Integer> receivedOneResult;
	private long gameQueueLastModTime = -1;
	
	public LadderGameStorage()
	{
		gamesInProgress = new HashMap<Integer, Game>();
		allGames = new TreeMap<Integer, Game>();
		receivedOneResult = new Vector<Integer>();
	}
	
	private void updateGameQueue() throws Exception
	{
		// first check for rare situation where a game we think is in progress (two results have not returned) is actually not (maybe a TM client crashed)
		// this actually gets called when server is initializing, so the server instance may be still null at this point.
		if (Server.Instance() != null)
		{
			Vector<Integer> playing = Server.Instance().getGamesInProgress();
			boolean foundFinishedGame = false;
			for (int gameID : gamesInProgress.keySet())
			{
				if (!playing.contains(gameID))
				{
					removeGame(gameID);
					foundFinishedGame = true;
				}
			}
			if (foundFinishedGame)
			{
				// games queue is already up to date now
				return;
			}
		}
		
		//throws exception if no lock possible in 10 seconds
		FileUtils.lockFile(ServerSettings.Instance().GamesListFile + ".lock", 100, 100, 60000);
		TreeMap<Integer, Game> games = new TreeMap<Integer, Game>();
		
		//if the file has NOT been modified since the last time it was read, return; otherwise update mod time 
		File gameQueue = new File(ServerSettings.Instance().GamesListFile);
		if (gameQueue.lastModified() > gameQueueLastModTime)
		{
			gameQueueLastModTime = gameQueue.lastModified();
		}
		else
		{
			try
			{
				FileUtils.unlockFile(ServerSettings.Instance().GamesListFile + ".lock");
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			return;
		}
		
		try (BufferedReader br = new BufferedReader(new FileReader(ServerSettings.Instance().GamesListFile)))
		{
			String line;
			while ((line = br.readLine()) != null)
			{
				if (line.trim().length() > 0)
				{
					Game game = GameParser.parseGame(line);
					games.put(game.getGameID(), game);
				}
			}
			allGames = games;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		try
		{
			FileUtils.unlockFile(ServerSettings.Instance().GamesListFile + ".lock");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public synchronized boolean hasMoreGames() throws Exception
	{
		updateGameQueue();
		return allGames.size() > gamesInProgress.size();
	}

	/**
	 * @param freeClientProperties for each free TM client, a list of properties form it's client_settings.json.
	 * We don't care which clients are which here, only if a game has SOME two clients that could host it.
	 * @return next Game to play.
	 * @throws Exception 
	 */
	public synchronized Game getNextGame(Vector<Vector<String>> freeClientProperties) throws Exception
	{
		return getNextGame(null, freeClientProperties);
	}
	
	
	/**
	 * @param currentHosts list of bots currently hosting games in lobby. With BWAPI >=4.2.0 bots only we can start simultaneous games if all hosts in lobby are different. 
	 * @param freeClientProperties for each free TM client, a list of properties form it's client_settings.json.
	 * We don't care which clients are which here, only if a game has SOME two clients that could host it.
	 * @return next Game to play.
	 * @throws Exception 
	 */
	public synchronized Game getNextGame(Collection<String> currentHosts, Vector<Vector<String>> freeClientProperties) throws Exception
	{
		updateGameQueue();
		//Rounds may not be used for ladder games but leaving the functionality intact for now
		
		//if Server File IO is turned on we need to completely finish one round before starting a game from a new one.
		boolean waitForPreviousRound = ServerSettings.Instance().EnableBotFileIO;
		
		//if bot File IO is turned on, don't return a game if all games from previous rounds have not already been removed
		int currentRound = allGames.get(allGames.firstKey()).getRound();
		for (int i = allGames.firstKey(); i <= allGames.lastKey(); i++)
		{	
			//skip games already in progress or finished
			if (gamesInProgress.containsKey(i) || !allGames.containsKey(i))
			{
				continue;
			}
			
			// break if we've entered another round (if we need to wait)
			if (waitForPreviousRound && allGames.get(i).getRound() != currentRound) {
				break;
			}
			
			//if there are current hosts, we skip games with a host which is currently hosting another game in the lobby
			if (currentHosts != null && currentHosts.contains(allGames.get(i).getHomebot().getName()))
			{
				continue;
			}
			//check for bot requirements
			if (canMeetGameRequirements(freeClientProperties, allGames.get(i)))
			{
				gamesInProgress.put(i, allGames.get(i));
				return allGames.get(i);
			}
		} 
		
		//returns null if no game can be started right now
		return null;
	}

	public synchronized Game lookupGame(int gameID)
	{
		return allGames.get(gameID);
	}

	public synchronized int getNumGamesRemaining() throws Exception
	{
		updateGameQueue();
		return allGames.size() - gamesInProgress.size();
	}

	public synchronized int getNumTotalGames() throws Exception
	{
		updateGameQueue();
		return allGames.size();
	}
	
	public synchronized void receivedResult(int gameID) throws Exception
	{
		if (receivedOneResult.contains(gameID))
		{
			receivedOneResult.removeElement(gameID);
			removeGame(gameID);
		}
		else
		{
			receivedOneResult.add(gameID);
		}
	}

	public synchronized void removeGame(int gameID) throws Exception {
		//throws exception if no lock possible
		//keep trying for remove game for 10 seconds
		FileUtils.lockFile(ServerSettings.Instance().GamesListFile + ".lock", 100, 100, 60000);
		TreeMap<Integer, Game> newGamesList = new TreeMap<Integer, Game>();
		
		StringBuilder out = new StringBuilder();
		
		// read every line/game in current file and write out all except the one to be removed
		// the games file should not be large
		try (BufferedReader br = new BufferedReader(new FileReader(ServerSettings.Instance().GamesListFile)))
		{
			String line;
			while ((line = br.readLine()) != null)
			{
				if (line.trim().length() > 0)
				{
					Game game = GameParser.parseGame(line);
					if (game.getGameID() != gameID)
					{
						out.append(line.trim() + System.getProperty("line.separator"));
						newGamesList.put(game.getGameID(), game);
					}
					
				}
			}
			br.close();
			FileUtils.writeToFile(out.toString(), ServerSettings.Instance().GamesListFile, false);
			
			//update game lists
			allGames = newGamesList;
			gamesInProgress.remove(gameID);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		try
		{
			FileUtils.unlockFile(ServerSettings.Instance().GamesListFile + ".lock");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
