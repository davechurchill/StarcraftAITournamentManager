package objects;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

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
		    gamesToPlay.remove(it.next());
		}
	}
	
	public boolean hasMoreGames()
	{
		return !gamesToPlay.isEmpty();
	}
	
	public Game getNextGame(Collection<String> currentHosts, Vector<Vector<String>> freeClientProperties, boolean waitForPreviousRound)
	{
		//if bot File IO is turned on, don't return a game if all games from previous rounds have not already been removed
		int currentRound = gamesToPlay.get(gamesToPlay.firstKey()).getRound();
		for (int i = gamesToPlay.firstKey(); !waitForPreviousRound || allGames.get(i).getRound() == currentRound; i++)
		{
			if (gamesToPlay.get(i) == null)
			{
				continue;
			}
			if (currentHosts != null && currentHosts.contains(gamesToPlay.get(i).getHomebot().getName()))
			{
				continue;
			}
			
			//check for bot requirements
			
			//check all combinations of free clients to see if there are two that match
			for (int j = 0; j < freeClientProperties.size(); j++)
			{
				boolean hasAllHomeProperties = true;
				for (String requirement : gamesToPlay.get(i).getHomebot().getRequirements())
				{
					if (!freeClientProperties.get(j).contains(requirement))
					{
						hasAllHomeProperties = false;
						break;
					}
				}
				
				if (hasAllHomeProperties)
				{
					for (int k = 0; k < freeClientProperties.size(); k++)
					{
						if (j != k)
						{
							boolean hasAllAwayProperties = true;
							for (String requirement : gamesToPlay.get(i).getAwaybot().getRequirements())
							{
								if (!freeClientProperties.get(k).contains(requirement))
								{
									hasAllAwayProperties = false;
									break;
								}
							}
							
							if (hasAllAwayProperties)
							{
								return gamesToPlay.remove(i);
							}
						}
					}
				}
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
}
