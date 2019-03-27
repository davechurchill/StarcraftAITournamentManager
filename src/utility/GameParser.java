package utility;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import objects.*;
import server.ServerSettings;

public class GameParser 
{
	private static TournamentGameStorage games;

	public static GameStorage getGames()
	{
		try
		{
			parse();
		}
		catch (Exception e)
		{
			System.err.println("Couldn't load games file list\n");
			e.printStackTrace();
			System.exit(-1);
		}
		
		return games;
	}

	private static void parse() throws NumberFormatException, Exception 
	{
		games = new TournamentGameStorage();
		try
		{
			if (!new File(ServerSettings.Instance().GamesListFile).exists())
			{
				return;
			}
			
			BufferedReader br = new BufferedReader(new FileReader(ServerSettings.Instance().GamesListFile));
			parseGames(br);
			br.close();
		}
		catch (FileNotFoundException e) 
		{
			System.out.println("Could not read settings file");
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			System.out.println("IOException while reading settings file");
			e.printStackTrace();
		}
	}
	
	private static void parseGames(BufferedReader br) throws Exception 
	{
		String line;
		
		while ((line = br.readLine()) != null)
		{
			if (line.trim().length() > 0)
			{
				//don't play games with excluded bots
				Game g = parseGame(line);
				if (ServerSettings.Instance().isExcludedBot(g.getHomebot().getName()) || ServerSettings.Instance().isExcludedBot(g.getAwaybot().getName()))
				{
					continue;
				}
				games.addGame(parseGame(line));
			}
		}
	}
	
	public static Game parseGame(String line) throws Exception
	{
		JsonObject game = Json.parse(line).asObject();
		
		int gameID = game.get("gameID").asInt();
		int roundID = game.get("roundID").asInt();
		String homeBot = game.get("homeBot").asString();
		String awayBot = game.get("awayBot").asString();
		String map = game.get("map").asString();
		
		return new Game(gameID, roundID, findBot(homeBot), findBot(awayBot), findMap(map));
	}
	
	private static Bot findBot(String name) throws Exception
	{
		for(Bot b : ServerSettings.Instance().BotVector)
		{
			if(b.getName().equals(name))
			{
				return b;
			}
		}
		
		throw new Exception("Bot not found!!\n Was looking for \"" + name + "\"");
	}
	
	private static Map findMap(String name) throws Exception
	{
		for (Map m : ServerSettings.Instance().MapVector)
		{
			if (m.getMapName().equals(name))
			{
				return m;
			}
		}
		
		throw new Exception("Map not found!!\n Was looking for\"" + name + "\"");
	}
}
