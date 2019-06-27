package objects;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

import server.ServerSettings;

public class TournamentGameStorage extends GameStorage
{
	private TreeMap<Integer, Game> gamesToPlay; //once a game from here is scheduled it is removed
	private HashMap<Integer, Game> allGames; // all games in tournament
	
	public TournamentGameStorage()
	{
		gamesToPlay = new TreeMap<Integer, Game>();
		allGames = new HashMap<Integer, Game>();
	}
	
	public void addGame(Game game)
	{
		gamesToPlay.put(game.getGameID(), game);
		allGames.put(game.getGameID(), game);
	}
	
	public void removePlayedGames(Collection<Integer> gameIDs)
	{
		Iterator<Integer> it = gameIDs.iterator();
		while (it.hasNext()) 
		{
			int current = it.next();
		    gamesToPlay.remove(current);
		}
	}
	
	public boolean hasMoreGames()
	{
		return !gamesToPlay.isEmpty();
	}
	
	/**
	 * @param freeClientProperties for each free TM client, a list of properties form it's client_settings.json.
	 * We don't care which clients are which here, only if a game has SOME two clients that could host it.
	 * @return next Game to play.
	 */
	public Game getNextGame(Vector<Vector<String>> freeClientProperties)
	{
		return getNextGame(null, freeClientProperties);
	}
	
	
	/**
	 * @param currentHosts list of bots currently hosting games in lobby. With BWAPI >=4.2.0 bots only we can start simultaneous games if all hosts in lobby are different. 
	 * @param freeClientProperties for each free TM client, a list of properties form it's client_settings.json.
	 * We don't care which clients are which here, only if a game has SOME two clients that could host it.
	 * @return next Game to play.
	 */
	public Game getNextGame(Collection<String> currentHosts, Vector<Vector<String>> freeClientProperties)
	{
		//if Server File IO is turned on we need to completely finish one round before starting a game from a new one.
		boolean waitForPreviousRound = ServerSettings.Instance().EnableBotFileIO;
		
		//if bot File IO is turned on, don't return a game if all games from previous rounds have not already been removed
		int currentRound = gamesToPlay.get(gamesToPlay.firstKey()).getRound();
		for (int i = gamesToPlay.firstKey(); !waitForPreviousRound || allGames.get(i).getRound() == currentRound; i++)
		{
			//skip games already in progress or finished
			if (!gamesToPlay.containsKey(i))
			{
				continue;
			}
			
			//if there are current hosts, we skip games with a host which is currently hosting another game in the lobby
			if (currentHosts != null && currentHosts.contains(gamesToPlay.get(i).getHomebot().getName()))
			{
				continue;
			}
			//check for bot requirements
			if (canMeetGameRequirements(freeClientProperties, gamesToPlay.get(i)))
			{
				return gamesToPlay.remove(i);
			}
		}
		
		//returns null if no game can be started right now
		return null;
	}
	
	public Game lookupGame(int gameID) 
	{
		return allGames.get(gameID);
	}
	
	public int getNumGamesRemaining()
	{
		return gamesToPlay.size();
	}
	
	public int getNumTotalGames()
	{
		return allGames.size();
	}
	
	public void removeGame(int gameID)
	{
		allGames.remove(gameID);
		gamesToPlay.remove(gameID);
	}
}
