package server;

import java.io.*;
import objects.TournamentModuleSettingsMessage;
import utility.FileUtils;

import java.util.Vector;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import objects.BWAPISettings;
import objects.Bot;
import objects.Map;

public class ServerSettings
{
	public Vector<Bot> 		BotVector 			= new Vector<Bot>();
	public Vector<Map> 		MapVector 			= new Vector<Map>();
	
	//The following 4 paths are hard coded here and not options in the settings file
	public String			ServerDir			= "./";
	public String			ServerReplayDir		= "replays/";
	public String			ServerRequiredDir	= "required/";
	public String			ServerBotDir		= "bots/";
	
	public int				ServerPort			= -1;
	public String			GamesListFile		= null;
	public String			ResultsFile			= null;
	public String 			ClearResults	 	= "ask";
	public boolean			DetailedResults		= false;
	public boolean			StartGamesSimul		= false;
	public String			TournamentType		= "AllVsAll";
	public boolean			EnableBotFileIO		= true;
	public Vector<String>	ExcludeFromResults	= new Vector<String>();
	
	public BWAPISettings	bwapi = new BWAPISettings();
	
	public TournamentModuleSettingsMessage tmSettings = new TournamentModuleSettingsMessage();

	private static final ServerSettings INSTANCE = new ServerSettings();
	
	private ServerSettings()
	{
		
	}
	
	public static ServerSettings Instance() 
	{
        return INSTANCE;
    }
	
	public Bot getBotFromBotName(String botname)
	{
		for (Bot b : BotVector)
		{
			if (b.getName().equalsIgnoreCase(botname))
			{
				return b;
			}
		}
		
		return null;
	}
		
	public void parseSettingsFile(String filename)
	{	
		try
		{
			BufferedReader br = new BufferedReader(new FileReader("server_settings.JSON"));
			JsonObject jo = Json.parse(br).asObject();
			br.close();
			
			JsonArray bots = jo.get("bots").asArray();
			for (JsonValue botValue : bots)
			{
				JsonObject bot = botValue.asObject();
				JsonValue reqArray = bot.get("ClientRequirements");
				Vector<String> requirements = new Vector<String>();
				if (reqArray != null)
				{
					JsonArray reqs = reqArray.asArray();
					if (reqs.size() > 0)
					{
						for (JsonValue reqObject : reqs)
						{
							JsonObject req = reqObject.asObject();
							requirements.add(req.get("Property").asString());
						}
						
					}
				}
				BotVector.add(new Bot(bot.get("BotName").asString(),bot.get("Race").asString(), bot.get("BotType").asString(), bot.get("BWAPIVersion").asString(), requirements));
			}
			
			JsonArray maps = jo.get("maps").asArray();
			for (JsonValue mapValue : maps)
			{
				JsonObject map = mapValue.asObject();
				MapVector.add(new Map(map.get("mapFile").asString()));
			}
			
			GamesListFile = jo.get("gamesListFile").asString();
			ResultsFile = jo.get("resultsFile").asString();
			DetailedResults = jo.get("detailedResults").asBoolean(); 
			ServerPort = jo.get("serverPort").asInt();
			ClearResults = jo.get("clearResults").asString();
			StartGamesSimul = jo.get("startGamesSimultaneously").asBoolean();
			TournamentType = jo.get("tournamentType").asString();
			EnableBotFileIO = jo.get("enableBotFileIO").asBoolean();
			
			JsonArray excludedBots = jo.get("excludeFromResults").asArray();
			for (JsonValue excludedBot : excludedBots)
			{
				JsonObject exclude = excludedBot.asObject();
				ExcludeFromResults.add(exclude.get("BotName").asString());
			}
					
			JsonObject tmSettingsJO = jo.get("tournamentModuleSettings").asObject();
			tmSettings.LocalSpeed = tmSettingsJO.get("localSpeed").asInt();
			tmSettings.FrameSkip = tmSettingsJO.get("frameSkip").asInt();
			tmSettings.GameFrameLimit = tmSettingsJO.get("gameFrameLimit").asInt();
			tmSettings.DrawBotNames = tmSettingsJO.get("drawBotNames").asBoolean() ? "true" : "false";
			tmSettings.DrawTournamentInfo = tmSettingsJO.get("drawTournamentInfo").asBoolean() ? "true" : "false";
			tmSettings.DrawUnitInfo = tmSettingsJO.get("drawUnitInfo").asBoolean() ? "true" : "false";
			
			JsonArray limits = tmSettingsJO.get("timeoutLimits").asArray();
			for (JsonValue limitValue : limits)
			{
				JsonObject limit = limitValue.asObject();
				tmSettings.TimeoutLimits.add(limit.get("timeInMS").asInt());
				tmSettings.TimeoutBounds.add(limit.get("frameCount").asInt());
			}
			
		}
		catch (Exception e)
		{
			System.err.println("Error parsing settings file, exiting\n");
			e.printStackTrace();
			System.exit(-1);
		}
		
		if (!checkValidSettings())
		{
			System.err.println("\n\nError in server set-up, please check documentation: http://www.cs.mun.ca/~dchurchill/starcraftaicomp/tm.shtml#ss");
			System.exit(0);
		}
	}
		
	private boolean checkValidSettings()
	{
		boolean valid = true;
		
		// check if all setting variables are valid
		if (BotVector.size() <= 1) 		{ System.err.println("ServerSettings: Must have at least 2 bots in settings file"); valid = false; }
		if (MapVector.size() <= 0)		{ System.err.println("ServerSettings: Must have at least 1 map in settings file"); valid = false; }
		if (ServerDir == null)			{ System.err.println("ServerSettings: ServerDir not specified in settings file"); valid = false; }
		if (GamesListFile == null)		{ System.err.println("ServerSettings: GamesListFile not specified in settings file"); valid = false; }
		if (ResultsFile == null)		{ System.err.println("ServerSettings: ResultsFile must be specified in settings file"); valid = false; }
		if (ServerPort == -1)			{ System.err.println("ServerSettings: ServerPort must be specified as an integer in settings file"); valid = false; }
		
		if (!ClearResults.equalsIgnoreCase("yes") && !ClearResults.equalsIgnoreCase("no") && !ClearResults.equalsIgnoreCase("ask"))
		{
			System.err.println("ServerSettings: ClearResultsFile invalid option: " + ClearResults);
			valid = false;
		}
		
		// check if all required files are present
		if (!new File(ServerReplayDir).exists())
		{
			FileUtils.CreateDirectory(ServerReplayDir);
			if (!new File(ServerReplayDir).isDirectory())
			{
				System.err.println("ServerSettings: Replay Dir (" + ServerReplayDir + ") does not exist and could not be created");
				valid = false;
			}
		}
		if (!new File(ServerBotDir).exists()) 		{ System.err.println("ServerSettings: Bot Dir (" + ServerBotDir + ") does not exist"); valid = false; }
		if (!new File(ServerRequiredDir).exists()) 	{ System.err.println("ServerSettings: Required Files Dir (" + ServerRequiredDir + ") does not exist"); valid = false; }
		
		// check all bot directories
		for (Bot b : BotVector)
		{
			String botDir 		= ServerBotDir + b.getName() + "/";
			String botAIDir 	= botDir + "AI/";
			String botDLLFile	= botAIDir + b.getName() + ".dll";
			String botWriteDir 	= botDir + "write/";
			String botReadDir 	= botDir + "read/";
			String proxyScript	= botAIDir + "run_proxy.bat";
			String botBWAPIReq  = ServerRequiredDir + "Required_" + b.getBWAPIVersion() + ".zip";
			
			// Check if all the bot files exist
			if (!new File(botDir).exists()) 		{ System.err.println("Bot Error: " + b.getName() + " bot directory " + botDir + " does not exist."); valid = false; }
			if (!new File(botAIDir).exists()) 		{ System.err.println("Bot Error: " + b.getName() + " bot AI directory " + botAIDir + " does not exist."); valid = false; }
			if (!new File(botDLLFile).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot dll file " + botDLLFile + " does not exist."); valid = false; }
			if (!new File(botWriteDir).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot write directory " + botWriteDir + " does not exist."); valid = false; }
			if (!new File(botReadDir).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot read directory " + botReadDir + " does not exist."); valid = false; }
			if (!new File(botBWAPIReq).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot required BWAPI files " + botBWAPIReq + " does not exist."); valid = false; }
			
      // Check that the proxy script exists if the type of bot requires it
			if ((b.isProxyBot() || b.isMixedBot()) && !new File(proxyScript).exists())
			{ 
				System.err.println("Bot Error: " + b.getName() + " listed as proxy but " + proxyScript + " does not exist."); 
				valid = false; 
			}
		}
		
		// Check if all the maps exist
		/*for (Map m : MapVector)
		{
			String mapLocation = ServerRequiredDir + "Starcraft/" + m.getMapLocation();
			if (!new File(mapLocation).exists())
			{
				System.err.println("Map Error: " + m.getMapName() + " file does not exist at specified location: " + mapLocation); valid = false;
			}
		}*/
		
		return valid;
	}
}
