package client;

import java.io.*;
import java.util.*;

import objects.*;
import utility.FileUtils;
import utility.WindowsCommandTools;

public class ClientCommands
{
	public static void Client_InitialSetup()
	{
		Client.log("      Client_InitialSetup()\n");
		
		// Make sure Starcraft isn't running
		Client_KillStarcraft();
		
		// Set up local firewall access
		WindowsCommandTools.RunWindowsCommand("netsh firewall add allowedprogram program = " + ClientSettings.Instance().ClientStarcraftDir + "starcraft.exe name = Starcraft mode = ENABLE scope = ALL", true, false);
		WindowsCommandTools.RunWindowsCommand("netsh firewall add allowedprogram program = client.jar name = AIIDEClient mode = ENABLE scope = ALL", true, false);
		WindowsCommandTools.RunWindowsCommand("netsh firewall add portopening TCP 12345 \"Open Port 12345TCP\"", true, false);
		WindowsCommandTools.RunWindowsCommand("netsh firewall add portopening UDP 12345 \"Open Port 12345UDP\"", true, false);
				
		// Clean the Starcraft directory of old files and folders
		Client_CleanStarcraftDirectory();
	}
		
	public static void Client_RunBotScript()
	{
    Client.log("      Client_RunBotScript()\n");
        
		WindowsCommandTools.RunWindowsCommand(ClientSettings.Instance().ClientStarcraftDir + "bwapi-data\\AI\\run_proxy.bat", false, false);
	}
	
	public static void Client_ClearWriteDirectory()
	{
		Client.log("      Client_ClearWriteDirectory()\n");

		FileUtils.CleanDirectory(new File(ClientSettings.Instance().ClientStarcraftDir + "bwapi-data/write/"));
	}
	
	// makes edits to windows registry so Chaoslauncher knows where StarCraft is installed
	public static void Client_RegisterStarCraft()
	{
		Client.log("      Client_RegisterStarCraft()\n");
		
		// 32-bit machine StarCraft settings
		String sc32KeyName =     "HKEY_LOCAL_MACHINE\\SOFTWARE\\Blizzard Entertainment\\Starcraft";
		String sc32UserKeyName = "HKEY_CURRENT_USER\\SOFTWARE\\Blizzard Entertainment\\Starcraft";
		WindowsCommandTools.RegEdit(sc32KeyName,     "InstallPath", "REG_SZ",    ClientSettings.Instance().ClientStarcraftDir + "\\");
		WindowsCommandTools.RegEdit(sc32KeyName,     "Program",     "REG_SZ",    ClientSettings.Instance().ClientStarcraftDir + "StarCraft.exe");
		WindowsCommandTools.RegEdit(sc32KeyName,     "GamePath",    "REG_SZ",    ClientSettings.Instance().ClientStarcraftDir + "StarCraft.exe");
		WindowsCommandTools.RegEdit(sc32UserKeyName, "introX",      "REG_DWORD", "00000000");
		
		// 64-bit machine StarCraft settings
		String sc64KeyName =     "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Blizzard Entertainment\\Starcraft";
		String sc64UserKeyName = "HKEY_CURRENT_USER\\SOFTWARE\\Wow6432Node\\Blizzard Entertainment\\Starcraft";
		WindowsCommandTools.RegEdit(sc64KeyName, "InstallPath", "REG_SZ", ClientSettings.Instance().ClientStarcraftDir + "\\");
		WindowsCommandTools.RegEdit(sc64KeyName, "Program",     "REG_SZ", ClientSettings.Instance().ClientStarcraftDir + "StarCraft.exe");
		WindowsCommandTools.RegEdit(sc64KeyName, "GamePath",    "REG_SZ", ClientSettings.Instance().ClientStarcraftDir + "StarCraft.exe");
		WindowsCommandTools.RegEdit(sc64UserKeyName, "introX",      "REG_DWORD", "00000000");
	}	
	
	public static void Client_KillStarcraft()
	{
		Client.log("      Client_KillStarcraft()\n");
		
		while (WindowsCommandTools.IsWindowsProcessRunning("StarCraft.exe"))
		{
			System.out.println("Killing Starcraft...  ");
			WindowsCommandTools.RunWindowsCommand("taskkill /T /F /IM StarCraft.exe", true, false);
			try { Thread.sleep(100); } catch (InterruptedException e) {}
		}
	}
	
	public static void Client_KillExcessWindowsProccess(Vector<Integer> startingProc)
	{
		Client.log("      Client_KillExcessWindowsProccess()\n");
		
		// Kill any processes that weren't running before startcraft started
		// This is helpful to kill any proxy bots or java threads that may still be going
		WindowsCommandTools.KillExcessWindowsProccess(startingProc);
	}
	
	public static void Client_CleanStarcraftDirectory()
	{
		Client.log("      Client_CleanStarcraftDirectory()\n");
		
		// Sleep for a second before deleting local directories
		try 
		{ 
			Thread.sleep(2000); 
		
			// Delete local folders which now contain old data
			FileUtils.DeleteDirectory(new File(ClientSettings.Instance().ClientStarcraftDir + "bwapi-data"));
			FileUtils.DeleteDirectory(new File(ClientSettings.Instance().ClientStarcraftDir + "characters"));
			FileUtils.DeleteDirectory(new File(ClientSettings.Instance().ClientStarcraftDir + "maps"));
			
			// Delete the old game state file
			File oldGameState = new File(ClientSettings.Instance().ClientStarcraftDir + "gameState.txt");
			while (oldGameState.exists()) 
			{
				System.out.println("Old game state file exists, deleting... ");
				oldGameState.delete();
				try { Thread.sleep(100); } catch (InterruptedException e) {}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void Client_RenameCharacterFile(InstructionMessage instructions)
	{
		Client.log("      Client_RenameCharacterFile()\n");
		String botName = instructions.isHost ? instructions.hostBot.getName() : instructions.awayBot.getName();
		String charDir = ClientSettings.Instance().ClientStarcraftDir + "characters\\";
		
		WindowsCommandTools.RunWindowsCommand("RENAME " + charDir + "*.mpc " + botName + ".mpc", true, false);
		WindowsCommandTools.RunWindowsCommand("RENAME " + charDir + "*.spc " + botName + ".spc", true, false);
	}
	
    public static void Client_StartStarcraft()
	{
		Client.log("      Client_StartStarcraft()\n");
		
		// Launch Starcraft, do not wait for this to finish, exit if it fails (false, true)
		WindowsCommandTools.RunWindowsExeLocal(ClientSettings.Instance().ClientStarcraftDir, "injectory.x86.exe --launch StarCraft.exe --inject bwapi-data\\BWAPI.dll --set-flags SEM_NOGPFAULTERRORBOX", false, true);
	}

	public static void Client_WriteTournamentModuleSettings(TournamentModuleSettingsMessage tmSettings)  
	{
		Client.log("      Client_WriteTournamentModuleSettings()\n");
		String tmSettingsFile = ClientSettings.Instance().ClientStarcraftDir + "\\bwapi-data\\tm_settings.ini";
		
		String tm = tmSettings.getSettingsFileString();
		
		try 
		{
			BufferedWriter out = new BufferedWriter(new FileWriter(new File(tmSettingsFile)));
			out.write(tm);
			out.close();
		} 
		catch (Exception e) 
		{
			System.err.println("Error: " + e.getMessage());
		}
	}
	
	public static void Client_WriteBWAPISettings(InstructionMessage instructions)  
	{
		String newLine = System.getProperty("line.separator");
		
		Client.log("      Client_WriteBWAPISettings()\n");
		String bwapiDest = ClientSettings.Instance().ClientStarcraftDir + "\\bwapi-data\\bwapi.ini";
		BWAPISettings bwapi = instructions.bwapi;
		Bot thisBot  = instructions.isHost ? instructions.hostBot : instructions.awayBot;
		Bot otherBot = instructions.isHost ? instructions.awayBot : instructions.hostBot;
		int id = instructions.game_id;
	
		String BWINI = "";
		
		BWINI += ";BWAPI written by AIIDE Tournament Manager " + newLine;
		
		BWINI += "[ai]" + newLine;
		BWINI += "; Paths and revisions for AI" + newLine;
		BWINI += ";   - Use commas to specify AI for multiple instances." + newLine;
		BWINI += ";   - If there are more instances than the amount of " + newLine;
		BWINI += ";         DLLs specified, then the last entry is used." + newLine;
		BWINI += ";   - Use a colon to forcefully load the revision specified." + newLine;
		BWINI += ";   - Example: SomeAI.dll:3400, SecondInstance.dll, ThirdInstance.dll" + newLine;
		BWINI += "ai     = bwapi-data\\AI\\" + thisBot.getName() + ".dll" + newLine;
		BWINI += "ai_dbg = bwapi-data\\AI\\" + thisBot.getName() + ".dll" + newLine + newLine;

		BWINI += "; Used only for tournaments" + newLine;
		BWINI += "; Tournaments can only be run in RELEASE mode" + newLine;
		BWINI += "tournament = " + ClientSettings.Instance().TournamentModuleFilename + newLine + newLine;

		BWINI += "[auto_menu]" + newLine;
		BWINI += "; auto_menu = OFF | SINGLE_PLAYER | LAN | BATTLE_NET" + newLine;
		BWINI += "; for replays, just set the map to the path of the replay file" + newLine;
		BWINI += "auto_menu = " + bwapi.auto_menu + newLine + newLine;
		
		if (thisBot.getBWAPIVersion().equals("BWAPI_420"))
		{
			BWINI += "; character_name = FIRST | WAIT | <other>" + newLine;
			BWINI += "; if FIRST (default), use the first character in the list" + newLine;
			BWINI += "; if WAIT, stop at this screen" + newLine;
			BWINI += "; else the character with the given value is used/created" + newLine;
			BWINI += "character_name = " + bwapi.character_name + newLine + newLine;
		}

		BWINI += "; pause_dbg = ON | OFF" + newLine;
		BWINI += "; This specifies if auto_menu will pause until a debugger is attached to the process." + newLine;
		BWINI += "; Only works in DEBUG mode." + newLine;
		BWINI += "pause_dbg = " + bwapi.pause_dbg + newLine + newLine;

		BWINI += "; lan_mode = Same as the text that appears in the multiplayer connection list" + newLine;			// FINISH
		BWINI += ";            Examples: Local Area Network (UDP), Local PC, Direct IP" + newLine;
		BWINI += "lan_mode = " + bwapi.lan_mode + newLine + newLine;

		BWINI += "; auto_restart = ON | OFF" + newLine;
		BWINI += "; if ON, BWAPI will automate through the end of match screen and start the next match" + newLine;
		BWINI += "; if OFF, BWAPI will pause at the end of match screen until you manually click OK," + newLine;
		BWINI += "; and then BWAPI resume menu automation and start the next match" + newLine;
		BWINI += "auto_restart = " + bwapi.auto_restart + newLine + newLine;

		BWINI += "; map = path to map relative to Starcraft folder, i.e. map = maps\\(2)Boxer.scm" + newLine;
		BWINI += "; leaving this field blank will join a game instead of creating it" + newLine;
		BWINI += "; The filename(NOT the path) can also contain wildcards, example: maps\\(?)*.sc?" + newLine;
		BWINI += "; A ? is a wildcard for a single character and * is a wildcard for a string of characters" + newLine;
		BWINI += "map = " + bwapi.map + newLine + newLine;

		BWINI += "; game = name of the game to join" + newLine;
		BWINI += ";	i.e., game = BWAPI" + newLine;
		BWINI += ";	will join the game called \"BWAPI\"" + newLine;
		BWINI += ";	If the game does not exist and the \"map\" entry is not blank, then the game will be created instead" + newLine;
		BWINI += ";	If this entry is blank, then it will follow the rules of the \"map\" entry" + newLine;
		BWINI += "game = " + instructions.hostBot.getName() + newLine + newLine; 

		BWINI += "; mapiteration =  RANDOM | SEQUENCE" + newLine;
		BWINI += "; type of iteration that will be done on a map name with a wildcard" + newLine;
		BWINI += "mapiteration = " + bwapi.mapiteration + newLine + newLine;

		BWINI += "; race = Terran | Protoss | Zerg | Random" + newLine;
		BWINI += "race = " + thisBot.getRace() + newLine + newLine;

		BWINI += "; enemy_count = 1-7, for 1v1 games, set enemy_count = 1" + newLine;
		BWINI += "; only used in single player games" + newLine;
		BWINI += "enemy_count = " + bwapi.enemy_count + newLine + newLine;

		BWINI += "; enemy_race = Terran | Protoss | Zerg | Random | RandomTP | RandomTZ | RandomPZ" + newLine;
		BWINI += "; only used in single player games" + newLine;
		BWINI += "enemy_race = " + bwapi.enemy_race + newLine + newLine;

		BWINI += "; enemy_race_# = Default" + newLine;
		BWINI += "; Values for enemy_race are acceptable, Default will use the value specified in enemy_race" + newLine;
		BWINI += "enemy_race_1 = " + bwapi.enemy_race_1 + newLine;
		BWINI += "enemy_race_2 = " + bwapi.enemy_race_2 + newLine;
		BWINI += "enemy_race_3 = " + bwapi.enemy_race_3 + newLine;
		BWINI += "enemy_race_4 = " + bwapi.enemy_race_4 + newLine;
		BWINI += "enemy_race_5 = " + bwapi.enemy_race_5 + newLine;
		BWINI += "enemy_race_6 = " + bwapi.enemy_race_6 + newLine;
		BWINI += "enemy_race_7 = " + bwapi.enemy_race_7 + newLine;

		BWINI += ";game_type = TOP_VS_BOTTOM | MELEE | FREE_FOR_ALL | ONE_ON_ONE | USE_MAP_SETTINGS | CAPTURE_THE_FLAG" + newLine;
		BWINI += ";           | GREED | SLAUGHTER | SUDDEN_DEATH | TEAM_MELEE | TEAM_FREE_FOR_ALL | TEAM_CAPTURE_THE_FLAG" + newLine;
		BWINI += "game_type = " + bwapi.game_type + newLine + newLine;
		
		if (thisBot.getBWAPIVersion().equals("BWAPI_420"))
		{
			BWINI += "; game_type_extra = Text that appears in the drop-down list below the Game Type drop-down list." + newLine;
			BWINI += "; If empty, the Starcraft default will be used." + newLine;
			BWINI += "; The following are the game types that use this setting, and corresponding example values" + newLine;
			BWINI += ";   TOP_VS_BOTTOM          3 vs 1 | 2 vs 2 | 1 vs 3 | # vs #" + newLine;
			BWINI += ";   GREED                  2500 | 5000 | 7500 | 10000" + newLine;
			BWINI += ";   SLAUGHTER              15 | 30 | 45 | 60" + newLine;
			BWINI += ";   TEAM_MELEE             2 | 3 | 4 | 5 | 6 | 7 | 8" + newLine;
			BWINI += ";   TEAM_FREE_FOR_ALL      2 | 3 | 4 | 5 | 6 | 7 | 8" + newLine;
			BWINI += ";   TEAM_CAPTURE_THE_FLAG  2 | 3 | 4 | 5 | 6 | 7 | 8" + newLine;
			BWINI += "game_type_extra = " + bwapi.game_type_extra + newLine + newLine;
		}

		BWINI += "; save_replay = path to save replay to" + newLine;
		BWINI += "; Accepts all environment variables including custom variables. See wiki for more info." + newLine;
		int thisBotNameLength = Math.min(4, thisBot.getName().length());
		int otherBotNameLength = Math.min(4, otherBot.getName().length());
		String repString = "maps\\replays\\" + thisBot.getName().toUpperCase() + "\\" + String.format("%05d", id) + "-" + thisBot.getName().substring(0,thisBotNameLength).toUpperCase() + "_" + otherBot.getName().substring(0,otherBotNameLength).toUpperCase() + ".REP" + newLine + newLine;
		BWINI += "save_replay = " + repString; 

		BWINI += "; wait_for_min_players = #" + newLine;
		BWINI += "; # of players to wait for in a network game before starting." + newLine;
		BWINI += "; This includes the BWAPI player. The game will start immediately when it is full." + newLine;
		BWINI += "wait_for_min_players = " + bwapi.wait_for_min_players + newLine + newLine;

		BWINI += "; wait_for_max_players = #" + newLine;
		BWINI += "; Start immediately when the game has reached # players." + newLine;
		BWINI += "; This includes the BWAPI player. The game will start immediately when it is full." + newLine;
		BWINI += "wait_for_max_players = " + bwapi.wait_for_max_players + newLine + newLine;

		BWINI += "; wait_for_time = #" + newLine;
		BWINI += "; The time in milliseconds (ms) to wait after the game has met the min_players requirement." + newLine;
		BWINI += "; The game will start immediately when it is full." + newLine;
		BWINI += "wait_for_time = " + bwapi.wait_for_time + newLine + newLine;

		BWINI += "[config]" + newLine;
		BWINI += "; holiday = ON | OFF" + newLine;
		BWINI += "; This will apply special easter eggs to the game when it comes time for a holiday." + newLine;
		BWINI += "holiday = " + bwapi.holiday + newLine + newLine;

		BWINI += "; show_warnings = YES | NO" + newLine;
		BWINI += "; Setting this to NO will disable startup Message Boxes, but also disable options that" + newLine;
		BWINI += "; assist in revision choice decisions." + newLine;
		BWINI += "show_warnings = " + bwapi.show_warnings + newLine + newLine;

		BWINI += "; shared_memory = ON | OFF" + newLine;
		BWINI += "; This is specifically used to disable shared memory (BWAPI Server) in the Windows Emulator \"WINE\"" + newLine;
		BWINI += "; Setting this to OFF will disable the BWAPI Server, default is ON" + newLine;
		BWINI += "shared_memory = " + bwapi.shared_memory + newLine + newLine;

		BWINI += "[window]" + newLine;
		BWINI += "; These values are saved automatically when you move, resize, or toggle windowed mode" + newLine;

		BWINI += "; windowed = ON | OFF" + newLine;
		BWINI += "; This causes BWAPI to enter windowed mode when it is injected." + newLine;
		BWINI += "windowed = " + bwapi.windowed + newLine + newLine;

		BWINI += "; left, top" + newLine;
		BWINI += "; Determines the position of the window" + newLine;
		BWINI += "left = " + bwapi.left + newLine;
		BWINI += "top  = " + bwapi.top + newLine + newLine;

		BWINI += "; width, height" + newLine;
		BWINI += "; Determines the width and height of the client area and not the window itself" + newLine;
		BWINI += "width  = " + bwapi.width + newLine;
		BWINI += "height = " + bwapi.height + newLine + newLine;

		BWINI += "[starcraft]" + newLine;
		BWINI += "; Game sound engine = ON | OFF" + newLine;
		BWINI += "sound = " + bwapi.sound + "" + newLine + newLine;
		
		BWINI += "; Screenshot format = gif | pcx | tga | bmp" + newLine;
		BWINI += "screenshots = " + bwapi.screenshots + newLine + newLine;

		BWINI += "[paths]" + newLine;
		BWINI += "log_path = " + bwapi.log_path + "" + newLine;

		try 
		{
			BufferedWriter out = new BufferedWriter(new FileWriter(new File(bwapiDest)));
			out.write(BWINI);
			out.close();
		} 
		catch (Exception e) 
		{
			System.err.println("Error: " + e.getMessage());
		}
	}
}
