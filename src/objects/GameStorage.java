package objects;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

public class GameStorage 
{
	private TreeMap<Integer, Game> gamesToPlay;
	private HashMap<Integer, Game> allGames;
	
	public GameStorage()
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
			int id = it.next();
		    gamesToPlay.remove(id);
		    allGames.remove(id);
		}
	}
	
	public boolean hasMoreGames()
	{
		return !gamesToPlay.isEmpty();
	}
	
	// Get the next game that isn't being hosted by a host listed in currentHosts
	public Game getNextGame(Collection<String> currentHosts)
	{
		//don't return a game if all games from previous rounds have not already been removed
		int currentRound = gamesToPlay.get(gamesToPlay.firstKey()).getRound();
		for (int i = gamesToPlay.firstKey(); gamesToPlay.get(i).getRound() == currentRound; i++)
		{
			if (currentHosts.contains(gamesToPlay.get(i).getHomebot().getName()))
			{
				continue;
			}
			return gamesToPlay.remove(i);
		}
		//returns null if no game can be started right now
		return null;
	}
	
	// get the unplayed game with smallest gameID
	public Game getNextGame()
	{
		return gamesToPlay.remove(gamesToPlay.firstKey());
	} 

	public Game lookupGame(int gameID) 
	{
		return allGames.get(gameID);
	}
}
