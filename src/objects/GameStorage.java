package objects;

import java.util.Collection;
import java.util.Vector;

public abstract class GameStorage {
	public abstract boolean hasMoreGames() throws Exception;
	public abstract Game getNextGame(Vector<Vector<String>> freeClientProperties) throws Exception;
	public abstract Game getNextGame(Collection<String> currentHosts, Vector<Vector<String>> freeClientProperties) throws Exception;
	public abstract Game lookupGame(int gameID);
	public abstract int getNumGamesRemaining() throws Exception;
	public abstract int getNumTotalGames() throws Exception;
	public abstract void removeGame(int gameID) throws Exception;
	
	//checks all combinations of free clients to see if there are two that can match for a given game
	protected boolean canMeetGameRequirements(Vector<Vector<String>> freeClientProperties, Game game)
	{
		//loop over clients searching for suitable host client
		for (int i = 0; i < freeClientProperties.size(); i++)
		{
			boolean satisfiesHomeBotRequirements = true;
			for (String requirement : game.getHomebot().getRequirements())
			{
				//check for negated properties
				if (requirement.startsWith("!"))
				{
					if (freeClientProperties.get(i).contains(requirement.substring(1, requirement.length())))
					{
						satisfiesHomeBotRequirements = false;
						break;
					}
				}
				//check for required properties
				else if (!freeClientProperties.get(i).contains(requirement))
				{
					satisfiesHomeBotRequirements = false;
					break;
				}
				
			}
			
			if (satisfiesHomeBotRequirements)
			{
				//loop over clients searching for suitable away client
				for (int j = 0; j < freeClientProperties.size(); j++)
				{
					if (i != j)
					{
						boolean satisfiesAwayBotRequirements = true;
						for (String requirement : game.getAwaybot().getRequirements())
						{
							//check for negated properties
							if (requirement.startsWith("!"))
							{
								if (freeClientProperties.get(j).contains(requirement.substring(1, requirement.length())))
								{
									satisfiesAwayBotRequirements = false;
									break;
								}
							}
							//check for required properties
							else if (!freeClientProperties.get(j).contains(requirement))
							{
								satisfiesAwayBotRequirements = false;
								break;
							}
						}
						
						if (satisfiesAwayBotRequirements)
						{
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
