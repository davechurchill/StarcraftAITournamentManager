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
import objects.LobbyGameSpeed;
import objects.Map;

public class ServerSettings
{
	public Vector<Bot> 		BotVector 			= new Vector<Bot>();
	public Vector<Map> 		MapVector 			= new Vector<Map>();
	
	//The following 4 paths are hard coded here and are not options in the settings file
	public String			ServerDir			= "./";
	public String			ServerReplayDir		= "replays/";
	public String			ServerRequiredDir	= "required/";
	public String			ServerBotDir		= "bots/";
	
	//These are set in server_settings.json
	public int				ServerPort			= -1;
	public String           MapsFile            = "maps.zip";
	public String			GamesListFile		= null;
	public String			ResultsFile			= null;
	public String 			ClearResults	 	= "ask";
	public boolean			DetailedResults		= false;
	public boolean			StartGamesSimul		= false;
	public String			TournamentType		= "AllVsAll";
	public LobbyGameSpeed   lobbyGameSpeed      = LobbyGameSpeed.NORMAL;
	public boolean			EnableBotFileIO		= true;
	public boolean			LadderMode			= false;
	public boolean          WriteCrashLogs      = true;
	public String           CrashLogsDir       = "crash_logs";
	public Vector<String>	ExcludeFromResults	= new Vector<String>();
	
	public BWAPISettings	bwapi = new BWAPISettings();
	
	public TournamentModuleSettingsMessage tmSettings = new TournamentModuleSettingsMessage();

	private static final ServerSettings INSTANCE = new ServerSettings();
	
	private String settingsFile = "";
	
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
	
	public boolean isExcludedBot(String botname)
	{
		for (String excluded : ExcludeFromResults)
		{
			if (excluded.equals(botname))
			{
				return true;
			}
		}
		return false;
	}
	
	public void updateSettings()
	{
		parseSettingsFile(settingsFile);
	}
		
	public void parseSettingsFile(String filename)
	{	
		try
		{
			Vector<Map> newMapVector = new Vector<Map>();
			Vector<String>	newExcludeFromResults = new Vector<String>();
			TournamentModuleSettingsMessage newTmSettings = new TournamentModuleSettingsMessage();
			
			settingsFile = filename;
			
			FileUtils.lockFile(filename + ".lock", 25, 20, 60000);
			
			BufferedReader br = new BufferedReader(new FileReader(filename));
			JsonObject jo = Json.parse(br).asObject();
			br.close();
			
			FileUtils.unlockFile(filename + ".lock");
			
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
						for (JsonValue req : reqs)
						{
							requirements.add(req.asString());
						}
					}
				}
				Bot existingBot = getBotFromBotName(bot.get("BotName").asString());
				if (existingBot != null)
				{
					//update existing bot
					existingBot.setRace(bot.get("Race").asString());
					existingBot.setType(bot.get("BotType").asString());
					existingBot.setBWAPIVersion(bot.get("BWAPIVersion").asString());
					existingBot.setRequirements(requirements);
				}
				else
				{
					BotVector.add(new Bot(bot.get("BotName").asString(),bot.get("Race").asString(), bot.get("BotType").asString(), bot.get("BWAPIVersion").asString(), requirements));
				}
			}
			
			JsonArray maps = jo.get("maps").asArray();
			for (JsonValue mapValue : maps)
			{
				newMapVector.add(new Map(mapValue.asString()));
			}
			
			MapsFile =  jo.get("mapsFile").asString();
			GamesListFile = jo.get("gamesListFile").asString();
			ResultsFile = jo.get("resultsFile").asString();
			DetailedResults = jo.get("detailedResults").asBoolean(); 
			ServerPort = jo.get("serverPort").asInt();
			ClearResults = jo.get("clearResults").asString();
			StartGamesSimul = jo.get("startGamesSimultaneously").asBoolean();
			TournamentType = jo.get("tournamentType").asString();
			try {
				lobbyGameSpeed = LobbyGameSpeed.valueOf(jo.get("lobbyGameSpeed").asString().toUpperCase());
			}
			catch (Exception e1) {
				System.err.println("ServerSettings: lobbyGameSpeed must be one of \"Slowest\", \"Slower\", \"Slow\", \"Normal\", \"Fast\", \"Faster\",or \"Fastest\"");
				throw new Exception();
			}
			
			EnableBotFileIO = jo.get("enableBotFileIO").asBoolean();
			LadderMode = jo.get("ladderMode").asBoolean();
			WriteCrashLogs = jo.get("writeCrashLogs").asBoolean();
			CrashLogsDir = jo.get("crashLogDir").asString();
			
			JsonArray excludedBots = jo.get("excludeFromResults").asArray();
			for (JsonValue excludedBot : excludedBots)
			{
				newExcludeFromResults.add(excludedBot.asString());
			}
					
			JsonObject tmSettingsJO = jo.get("tournamentModuleSettings").asObject();
			newTmSettings.LocalSpeed = tmSettingsJO.get("localSpeed").asInt();
			newTmSettings.FrameSkip = tmSettingsJO.get("frameSkip").asInt();
			newTmSettings.GameFrameLimit = tmSettingsJO.get("gameFrameLimit").asInt();
			newTmSettings.DrawBotNames = tmSettingsJO.get("drawBotNames").asBoolean() ? "true" : "false";
			newTmSettings.DrawTournamentInfo = tmSettingsJO.get("drawTournamentInfo").asBoolean() ? "true" : "false";
			newTmSettings.DrawUnitInfo = tmSettingsJO.get("drawUnitInfo").asBoolean() ? "true" : "false";
			
			JsonArray limits = tmSettingsJO.get("timeoutLimits").asArray();
			for (JsonValue limitValue : limits)
			{
				JsonObject limit = limitValue.asObject();
				newTmSettings.TimeoutLimits.add(limit.get("timeInMS").asInt());
				newTmSettings.TimeoutBounds.add(limit.get("frameCount").asInt());
			}
			
			MapVector = newMapVector;
			ExcludeFromResults = newExcludeFromResults;
			tmSettings = newTmSettings;						
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
		if (!LadderMode && BotVector.size() <= 1) { System.err.println("ServerSettings: Must have at least 2 bots in settings file"); valid = false; }
		if (MapVector.size() <= 0)                { System.err.println("ServerSettings: Must have at least 1 map in settings file"); valid = false; }
		if (GamesListFile == null)                { System.err.println("ServerSettings: GamesListFile not specified in settings file"); valid = false; }
		if (ResultsFile == null)                  { System.err.println("ServerSettings: ResultsFile must be specified in settings file"); valid = false; }
		if (ServerPort == -1)                     { System.err.println("ServerSettings: ServerPort must be specified as an integer in settings file"); valid = false; }
		
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
		if (!new File(ServerRequiredDir + MapsFile).exists()) 	{ System.err.println("ServerSettings: Maps File (" + ServerRequiredDir + MapsFile + ") does not exist"); valid = false; }
		
		// check all bot directories
		for (Bot b : BotVector)
		{
			boolean botValid = true;
			
			String botDir 		= ServerBotDir + b.getName() + "/";
			String botAIDir 	= botDir + "AI/";
			String botDLLFile	= botAIDir + b.getName() + ".dll";
			String botWriteDir 	= botDir + "write/";
			String botReadDir 	= botDir + "read/";
			String proxyScript	= botAIDir + "run_proxy.bat";
			String botBWAPIReq  = ServerRequiredDir + "Required_" + b.getBWAPIVersion() + ".zip";
			
			//create the read and write dirs if they don't exist
			createBotRequiredDirs(b.getName());
			
			// Check if all the bot files exist
			if (!new File(botDir).exists()) 		{ System.err.println("Bot Error: " + b.getName() + " bot directory " + botDir + " does not exist."); botValid = false; }
			if (!new File(botAIDir).exists()) 		{ System.err.println("Bot Error: " + b.getName() + " bot AI directory " + botAIDir + " does not exist."); botValid = false; }
			if (!new File(botDLLFile).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot dll file " + botDLLFile + " does not exist."); botValid = false; }
			if (!new File(botWriteDir).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot write directory " + botWriteDir + " does not exist."); botValid = false; }
			if (!new File(botReadDir).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot read directory " + botReadDir + " does not exist."); botValid = false; }
			if (!new File(botBWAPIReq).exists()) 	{ System.err.println("Bot Error: " + b.getName() + " bot required BWAPI files " + botBWAPIReq + " does not exist."); botValid = false; }
			
			// Check if the bot is proxy and the proxy bot exists
			if (b.isProxyBot() && !new File(proxyScript).exists()) 
			{ 
				System.err.println("Bot Error: " + b.getName() + " listed as proxy but " + proxyScript + " does not exist."); 
				botValid = false; 
			}
			
			b.setValid(botValid);
			if (!LadderMode && !botValid)
			{
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
	
	private void createBotRequiredDirs(String botName)
	{
		File readDir = new File(ServerSettings.Instance().ServerBotDir + "/" + botName + "/read");
		if (!readDir.exists())
		{
			readDir.mkdirs();
		}
		File writeDir = new File(ServerSettings.Instance().ServerBotDir + "/" + botName + "/write");
		if (!writeDir.exists())
		{
			writeDir.mkdirs();
		}
	}
}