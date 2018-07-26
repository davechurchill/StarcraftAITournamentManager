package utility;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.util.Vector;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import objects.*;
import server.ServerSettings;

public class GameParser 
{
	private static GameStorage games;
	private static Vector<Bot> bots;
	private static Vector<Map> maps;

	public static GameStorage getGames(Vector<Bot> p_bots, Vector<Map> p_maps)
	{
		try
		{
			maps = p_maps;
			bots = p_bots;
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
		if (games == null) {
			games = new GameStorage();
		}
		
		try 
		{
			File gamesFile = new File(ServerSettings.Instance().GamesListFile);
			if (!gamesFile.exists())
			{
				return;
			}
			
			FileUtils.lockFile(ServerSettings.Instance().GamesListFile + ".lock", 5, 100, 60000);
		
			BufferedReader br = new BufferedReader(new FileReader(ServerSettings.Instance().GamesListFile));
			parseGames(br);
			br.close();
			
			// in ladder mode games list should be deleted after parsing
			FileUtils.DeleteFile(gamesFile);
			
			FileUtils.unlockFile(ServerSettings.Instance().GamesListFile + ".lock");
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
				JsonObject game = Json.parse(line).asObject();
				
				int gameID = game.get("gameID").asInt();
				int roundID = game.get("roundID").asInt();
				String homeBot = game.get("homeBot").asString();
				String awayBot = game.get("awayBot").asString();
				String map = game.get("map").asString();
				
				Game newGame = new Game(gameID, roundID, findBot(homeBot), findBot(awayBot), findMap(map)); 
				games.addGame(newGame);
			}
		}
	}
	
	private static Bot findBot(String name) throws Exception
	{
		for(Bot b : bots)
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
		for (Map m : maps)
		{
			if (m.getMapName().equals(name))
			{
				return m;
			}
		}
		
		throw new Exception("Map not found!!\n Was looking for\"" + name + "\"");
	}
}
