# StarcraftAITournamentManager
Open Source Tournament Manager Software for StarCraft: Broodwar AI Competitions

Created and maintained by [David Churchill](http://www.cs.mun.ca/~dchurchill/) and Rick Kelly, organizers of the [AIIDE Starcraft AI Competition](http://www.cs.mun.ca/~dchurchill/starcraftaicomp/)

![TM Server GUI Screenshot](http://www.cs.mun.ca/~dchurchill/starcraftaicomp/tm_server_gui.png)

## Table of Contents

* [Overview](#overview)
	* [Disclaimer](#disclaimer) 
* [Introduction](#introduction)
	* [Video](#video)
	* [Server](#server)
	* [Client](#client)
	* [Results Parser](#results-parser)
* [Instructions](#instructions)
	* [Prerequisites](#prerequisites)
	* [Download & Compile](#download--compile)
	* [Initial Server Setup](#initial-server-setup)
	* [Running Server Software](#running-server-software)
	* [Server GUI](#server-gui)
	* [Initial Client Setup](#initial-client-setup)
	* [Running Client Software](#running-client-software)
	* [Results Output](#results-output)
	* [Helpful Windows Commands](#helpful-windows-commands)
* [Settings](#settings)
	* [Server Settings](#server-settings)
	* [Client Settings](#client-settings)
* [Change Log](#change-log)

## Overview

This software is a tool for running Starcraft AI bot tournaments using [BWAPI](https://github.com/bwapi/bwapi).
It uses a server-client architecture with one machine acting as a server and any number of other machines acting as clients.
The tournament manager is written entirely in Java, and can be run on Windows 7 or higher, either on a physical machine or a virtual machine.
All data sent and received is compressed and passed through Java sockets, so no special network configuration is required to run the software.

This repository includes precompiled server and client jar files, as well as the complete Java 7 source code.
It also includes several required files for setup such as BWAPI .dll files which will automatically be configured and run for you. 
Also included are the bots and maps from the 2014-2016 AIIDE StarCraft AI Competitions (zipped).
With these files you should be able to run a tournament as quickly as you can install StarCraft on all of your client machines!

### Disclaimer

This software regularly creates, deletes, and sends files over sockets, use it at your own risk. 

## Introduction

### Video

The following video uses an older version of the tournament manager, but the set up procedure is still quite similar, and it demonstrates the main functionality of the software. The main difference from what you see in the video and the new software are that the settings files are now in JSON format, and the GUIs look slightly different. An updated video will be coming soon.

[![AIIDE Tournament Manager Software](http://img.youtube.com/vi/tl-nansNbsA/0.jpg)](http://www.youtube.com/watch?v=tl-nansNbsA)


### Server

When running the software, one machine acts as a server for the tournament.
The server is a central repository where all bot files (including file I/O) data, cumulative results, and replay files are stored.
The server also monitors each client remotely and outputs results data that can be viewed in html.
Tournament status can be viewed in real time via the server GUI.

The server program has a threaded component which monitors for new client connections and detects client disconnections, maintaining a current list of clients which can have one of the following statuses:
* **READY** - Client is free and ready to start a game of StarCraft
* **STARTING** - Client has started the StarCraft LAN lobby but the match has not yet begun
* **RUNNING** - Client is currently running a game of StarCraft
* **SENDING** - Client has finished the game and is sending results and data back to the server.

The server's main scheduling loop tries to schedule the next game from the games list every 2 seconds.
Normally a new game can be started only if:
1. two or more Clients are **READY**, and
2. no clients are **STARTING**.

The reason no clients can be **STARTING** is to prevent multiple StarCraft game lobbies to be open on the same LAN, which may cause mis-scheduled games due to limitations in BWAPI versions previous to 4.2.0 on how we are able to join games automatically.
If all bots use BWAPI 4.2.0 there is an option in the server settings file to enable multiple games to start at the same time.
Once these two conditions are met, the server sends the required bot files, map files, BWAPI version, and DLL injector to the client machines, specifying one client as the host and one as the away machine.
Those clients' status are then set to **STARTING**.

Each client is handled by a separate thread in the server, and if the client is **STARTING**, **RUNNING**, or **SENDING**, it sends periodic status updates back to the server for remote monitoring.
Data such as current game time, time-out information, map, game ID, etc are each updated once per second from each client to the server GUI.
When a client finishes a game the results are sent back to the server along with file I/O data and replay files, which are all stored on the server.
This process repeats until the tournament has finished.

Shutting down the server via the GUI will cause all client games to stop and all client software to shut down and clean up properly.
The tournament can be resumed upon re-launching the server program as long as the results file, games list, and settings file do not change.
If the server is shut down with games in progress (results not yet received by the server), those games will be rescheduled and played again if the same tournament is restarted.

### Client

The client software can be run on as many machines that are available on your LAN.
After an initial setup of the client machine (installing StarCraft, etc.) the client software connects to the server machine to await instructions.

The client machine will stay idle until it receives instructions from the server that a game is to be run.
Once the client receives the required files from the server, it ensures that no current StarCraft processes are running, records a current snapshot of the running processes on the client machine, writes the BWAPI settings file, and starts the game.
When the game starts, a custom BWAPI Tournament Module is injected which outputs a GameState file to disk every few frames, which monitors the current state of StarCraft.
The client software reads this file to check for various conditions such as bot time-outs, crashes, no game frame progression, and game termination.
As the game is running, the client sends the contents of the GameState file to the server once per second to be monitored on the server GUI.

Once the game has terminated for any reason, the results of the game, replay files, and file I/O data are sent back to the server.
Once the sending is complete, the client software shuts down any processes on the machine which were not running when the game began, to prevent things like crashed proxy bots or stray threads from hogging system resources from future games.
StarCraft is shut down, the machine is cleaned of any files written during the previous game, and the client status is reported back to the server as **READY**.

### Results Parser
The Tournament Manager comes with a stand-alone results parser that generates summary and detailed results using the games.txt, results.txt, and settings.json files found in the /server directory.
The results parser is useful if you want to view HTML results for different tournaments you've previously run by swapping out the games list and results files.

## Instructions

### Prerequisites

Running a tournament using this software requires the following prerequisites:

* Microsoft Windows 7 (or higher) (Clients)
* Physical or Virtual Machines with minimum 2 CPU cores (Clients)
* StarCraft: BroodWar (Clients only)
* Microsoft VC++ Redistributables (See Below, Clients only)
* Any prerequisites for bots in the tournament, BWTA/BWTA2 DLLs, specific JDK versions required by Java bots, etc. (Clients only)
* Java JDK 8 (Clients and Server)

Download a zip file containing all Visual Studio redists and easy install script here: [all_vcredist_x86.zip](http://www.cs.mun.ca/~dchurchill/starcraftaicomp/all_vcredist_x86.zip)

### Download & Compile

Download or clone the repository to any directory on your server machine that does not contain spaces. You will find the following directory structure:

    TournamentManager/
        client/                           Client Directory
            BWAPI.ini                         Default BWAPI settings file
            client.jar                        Client jar file
            client_settings.json              Client settings file (modify this)
            run_client.bat                    Script to run client
        server/                           Server Directory
            bots/                             Contains all files for each bot
                botname/                      Bot-specific directory
                    AI/                       Where the .dll / proxy bot files go
                    read/                     File I/O read directory
                    write/                    File I/O write directory
            html/                             HTML for viewing results
               css/                           CSS files
               javascript/                    Javascript files
               results/                       Output folder for results data in JSON format
               index.html                     Summary of tournament results 
               results.html                   Detailed results from each game
               win_percentage_graph.html      Win % over time for all bots
            replays/                          Replay storage directory * 
            required/                         Required file storage directory
                Required_BWAPI_374.zip        BWAPI/Starcraft required files (BWAPI 374)
                Required_BWAPI_401B.zip       BWAPI/Starcraft required files (BWAPI 401B)
                Required_BWAPI_412.zip        BWAPI/Starcraft required files (BWAPI 412)
                Required_BWAPI_420.zip        BWAPI/Starcraft required files (BWAPI 420)
            games.txt                         Default tournament games list filename *
            parse_results.bat                 Script to run results parser
            results.txt                       Default tournament results filename *
            results_parser.jar                Parses existing results like server without running server
            run_server.bat                    Script to run server
            server.jar                        Server .jar file
            server_settings.json              Server settings file (modify this)
        src/                              Source Code Folder
            packagename/                      Source package directories
            clean.bat                         Script to delete all .class files in subdirs
            make.bat                          Script to compile / make jar files
*doesn't exist by default, but server will create

The tournament manager comes pre-compiled as 2 jar files (client/client.jar, server/server.jar), however if you want to compile the code you can use make.bat in the src/ folder, or any other build system you wish. The make.bat script will run clean.bat (delete .class files), compile the necessary Java files, create the required .jar files and put them into the correct sub-directories.

### Initial Server Setup

1. Install Java JDK 7 or higher
1. (Windows) Edit system PATH to include jdk/bin directory for javac and java
1. Turn off firewall
1. The following directory structure must exist for each bot in the tournament:
	* server/bots/BotName/ - Bot directory
	* server/bots/BotName/AI/ - Holds all AI files for each bot including BotName.dll
	* server/bots/BotName/AI/BotName.dll - Bot .dll must be named the same as folder (the .dll file can be empty for a proxy bot, but must exist)
	* server/bots/BotName/AI/run_proxy.bat - If bot is a proxy bot, this file must exist
	* server/bots/BotName/read/ - Bot read directory
	* server/bots/BotName/write/ - Bot write directory
1. Put your map files inside the server/required/Required_*.zip files under the 'maps' folder

**Note:** The tournament manager comes with the bots and maps which competed in the 2014-2016 AIIDE StarCraft AI Competitions.
Make sure that each bot and map you change is listed correctly in server/server_settings.json 

**Proxy Bots:** The file server/bots/BotName/AI/run_proxy.bat is a file which must exist if your bot is listed as 'proxy' in server_settings.json, it will be run on the client machine immediately BEFORE StarCraft is launched, and must contain all code necessary to launch your proxy bot. All proxy bot files must be stored in the server/bots/BotName/AI/ directory (subdirectories allowed), since this is the folder which is copied to the client machine before a game starts.

### Running Server Software

1. Edit server/server_settings.json to suit your tournament needs
1. Run server/run_server.bat
1. If previous results exist, server will ask to resume tournament or delete previous results (under default configuration)
1. If a games list file does not exist, server will prompt to generate one. It can generate a round robin tournament or a 1 vs all tournament based on the bots and maps in server_settings.json.
Currently only round robin tournament games generation is supported, however you can edit the games list manually as long as you follow the existing syntax.
1. Server will check if required files exist before launching GUI
1. Once the GUI has started, you must manually launch client software on client machines

### Server GUI

The Server GUI displays information about the tournament in the top bar, followed by a list of connected clients and their current status, and a log at the bottom. Game duration is given in normal speed Starcraft time, though the game is likely playing much faster (depending on server settings and client hardware).

The menu options under "Actions" allow you to:
* Generate detailed results (normally this is the only way to generate detailed results, unless automatic generation is turned on in the server settings),
* Send a command to all clients, or
* Request a screenshot of a client's screen.

The same client options, plus the options to kill clients or filter the log by client, can be found by selecting and right-clicking on one or more clients in the client list.

### Initial Client Setup

1. Install StarCraft: BroodWar to a directory containing no spaces
1. Upgrade StarCraft to version 1.16.1
1. Install Microsoft Visual C++ Redists [(zip file containing all Visual Studio redists and easy install script)](http://www.cs.mun.ca/~dchurchill/starcraftaicomp/all_vcredist_x86.zip)
1. Install Java JDK 8 or higher
1. Edit system PATH to include jdk/bin directory for javac and java
1. 32bit Windows: Edit registry so that HKLM\SOFTWARE has "Full Control" for current user
1. 64bit Windows: Edit registry so that HKLM\SOFTWARE\Wow6432Node has "Full Control" for current user
1. Copy client/ directory to client machine

### Running Client Software

1. Edit client/client_settings.json to match your tournament setup
1. Run client/run_client.bat

Note: Exactly one client instance can be run per machine.
Clients can be run on a physical or a virtual machine (minimum 2 CPU cores), as long as your LAN settings can handle StarCraft's UDP communication.
One client can be run on the same machine as the server.
I regularly run sample tournaments with the server and one client running on a physical machine, with a 2nd client running in a VM.

The Client GUI has no options or actions you can take.
It only displays information about the current game or status, and a log.

### Results Output

* Raw results are written to **server/results.txt** after each game ends
* A JSON results summary is written every 2 seconds to **server/html/results/results_summary.js** and can be viewed at **server/html/index.html**
* Detailed match results can be written manually via the Server GUI, or automatically once every minute if you turn on the option in the server settings file.
It is off by default due to long processing times for very large tournaments.
* Replay files are saved to **server/replays/BotName**
* Bot read/write directories are stored in **server/bots/BotName/(read|write)**

**Note:** Detecting when a bot crashes is difficult, so a crash is recorded whenever the game doesn't progress for more than a minute.
In these cases the bot who recorded the higher frame count (meaning the game was running for longer) is declared the winner.
This means it isn't possible to distinguish between a crash and a case of a bot taking more than a minute to process a single frame of the game.

Crashes in which the game never starts (frame count for both bots is zero) are not counted in the results summary in **html/index.html**.
In Detailed results these games are listed with an arbitrary winner and loser, but the crashing bot is listed as "unknown".

Games that last more frames than `gameFrameLimit` in server_settings.json (default setting is equal to one hour at normal speed) are terminated, and the winner is the bot with the higher score.
These losses are reported as "Game Timeout" in the results summary and "Timeout" in the detailed results page.

### Helpful Windows commands

If you are running a tournament from a Windows server, you can use [PSExec](https://docs.microsoft.com/en-us/sysinternals/downloads/psexec) to start your clients from the server rather than having to start the clients manually on each machine.

.bat file for starting clients (NOT INCLUDED IN THE REPOSITORY):

```bat
for /F "tokens=*" %%A in (client_list.txt) do PsExec.exe -i -d \\%%A cmd /S /C "cd c:\tm\client\ & java -jar client.jar client_settings.json"
```

where client_list.txt contains:

```
192.168.1.102 -u username1 -p password1
192.168.1.103 -u username2 -p password2
```

If you're making changes to the software or your client settings you might want to use commands like this to transfer new versions to your client machines:

```bat
for /F "tokens=*" %%A in (client_list.txt) do PsExec.exe -i -d \\%%A -c -v ..\client\client.jar cmd /S /C ""
for /F "tokens=*" %%A in (client_list.txt) do PsExec.exe -i -d \\%%A cmd /S /C "move /Y c:\Windows\client.jar c:\TM\client\"
```

## Settings

### Server Settings

All server configuration is done in /server/server_settings.json.
This file must parse as valid JSON or the server will not start.


<table>
<tr><th>Name</th><th>Value</th></tr>
<tr>
	<td>bots</td>
	<td>
        <b>Type:</b> Array of json objects<br><br>
        These are the bots that will play in the tournament.
        Each bot object must contain the following name/value pairs:
        <ul>
        <li><b>BotName:</b> String - the name of the bot, matching the bot folder name</li>
        <li><b>Race:</b> "Random" | "Terran" | "Zerg" | "Protoss"</li>
        <li><b>BotType:</b> "dll" | "proxy"</li>
        <li><b>BWAPIVersion:</b> "BWAPI_374" | "BWAPI_401B" | "BWAPI_412" | "BWAPI_420"</li>
        <li><b>ClientRequirements</b> (OPTIONAL): array of json objects with required properties</li>
        	<ul>
            	<li>Example: [{"Property": "GPU"}, {"Property": "Extra RAM"}]
                <li>Bot requirements must match a client in the tournament (see Client Settings) or the tournament will not be able to finish
            </ul>
        </ul>
     	Example: {"BotName": "UAlbertaBot", "Race": "Random", "BotType": "proxy", "BWAPIVersion": "BWAPI_420"}
    </td>
</tr>
<tr>
	<td>maps</td>
	<td>
    	<b>Type:</b> Array of json objects<br><br>
        Each round of the tournament will be played on these maps in the order they are listed in. Each map object must contain the following name/value pair:
        <ul>
        <li><b>mapFile:</b> String - path to the map relative to the Starcraft directory; no spaces</li>
        </ul>
     	Example: {"mapFile": "maps/aiide/(2)Benzene.scx"}
    </td>
</tr>
<tr>
	<td>gamesListFile</td>
	<td>
    	<b>Type:</b> String<br><br>
    	Location of file with list of games to be played, relative to server.jar; No spaces.
        The user will be prompted to generate a new games list if the file does not already exist (i.e. if this is a new tournament).
    </td>
</tr>
<tr>
	<td>resultsFile</td>
	<td>
    	<b>Type:</b> String<br><br>
    	Location of tournament results file, relative to server.jar. No spaces. Raw results data returned from clients is stored in this file (one line for each client). Nice results are output by the server in the html/ directory.
    </td>
</tr>
<tr>
	<td>DetailedResults</td>
	<td>
    	<b>Type:</b> Boolean<br><br>
    	Setting to true auto-generates detailed results every minute.
        Generating detailed results gets slow for very large tournaments, so default is false.
        You can manually generate the results from the Actions menu in the server, which is recommended.
    </td>
</tr>
<tr>
	<td>serverPort</td>
	<td>
    	<b>Type:</b> Number<br><br>
    	Port to listen for clients on. This should match the port number in the client's <b>ServerAddress</b> setting.
    </td>
</tr>
<tr>
	<td>clearResults</td>
	<td>
    	<b>Type:</b> String<br><br>
    	Clear existing results on server start? Allowed values: "yes" | "no" | "ask"<br>
        If "yes" then a new tournament is always started when the server is started. If "no" then an existing tournament will be resumed if possible.
    </td>
</tr>
<tr>
	<td>startGamesSimultaneously</td>
	<td>
    	<b>Type:</b> Boolean<br><br>
    	If set to <b>true</b> new games will be started while other games are still in the starting process (i.e. other Starcraft instances are in the lobby).
		If set to <b>false</b> only one game can be <b>STARTING</b> at a time.<br><br>
        <b>WARNING:</b> This is only useable if all bots are using BWAPI version 4.2.0 or later.
        If using older versions of BWAPI, bots will join any game in the lobby, leading to games with more than 2 players, and generally games that do not match.
    </td>
</tr>
<tr>
	<td>tournamentType</td>
	<td>
    	<b>Type:</b> String<br><br>
    	Allowed values: "AllVsAll" | "1VsAll"<br>
        <ul>
        	<li>AllVsAll - Standard round robin tournament</li>
            <li>1VsAll - First bot in <b>bots</b> list will play all the others.
         	Usefull for testing changes to your bot.</li>
        </ul>
    </td>
</tr>
<tr>
	<td>enableBotFileIO</td>
	<td>
    	<b>Type:</b> Boolean<br><br>
    	If set to <b>true</b> the server will wait for each round to complete before sarting the next round.
		Every time a round finishes the contents of 'BotName/write' will be copied to 'BotName/read'.
		Bots that implement learning from previous rounds will have access to the contents of the read directory in 'bwapi-data/read' on the client machine.
		If set to <b>false</b> the server will ignore round numbers when scheduling games, and never copy from 'write' to 'read'.
    </td>
</tr>
<tr>
	<td>excludeFromResults</td>
	<td>
    	<b>Type:</b> Array of json objects<br><br>
    	Bots listed in this array will be excluded from the results summary and detailed results output, but games that include them will still be played.
        This feature is useful if you need to disqualify a bot from a tournament, or want to see the overall effects of a bot on the results.<br><br>
        Each excluded bot object must contain the following name/value pair: <br>
        <ul>
        	<li><b>BotName:</b> String - Bot to be excluded</li>
        </ul>
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings</td>
	<td>
    	<b>Type:</b> Object<br><br>
    	Tournament Module settings control the tournament module DLL which is injected into each Starcraft instance with BWAPI.
        It controls game speed, draws information to the screen, and outputs data about the game being played so that the client can tell if a bot has crashed, timed out, etc.
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings<br>.localSpeed</td>
	<td>
    	<b>Type:</b> Number<br><br>
    	BWAPI Local Speed; Calls BWAPI::Broodwar->setLocalSpeed(SpeedValue).
        Set to 0 to run games at the fastest speed possible.
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings<br>.frameSkip</td>
	<td>
    	<b>Type:</b> Number<br><br>
    	BWAPI Frame Skip; Calls BWAPI::Broodwar->setFrameSkip(SkipValue)<br>
        This does nothing unless LocalSpeed is 0.
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings<br>.gameFrameLimit</td>
	<td>
    	<b>Type:</b> Number<br><br>
    	Game Frame Time Limit; Game stops when BWAPI::Broodwar->getFrameCount() > FrameLimit<br>
        If gameFrameLimit is 0, no frame limit is used.
        Normal Starcraft speed is 24 frames per second.
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings<br>.timeoutLimits</td>
	<td>
    	<b>Type:</b> Array of json objects<br><br>
    	Each timeoutLimit object must contain the following name/value pairs:
        <ul>
        	<li><b>timeInMS:</b> Number</li>
        	<li><b>frameCount:</b> Number</li>
        </ul>
        A bot loses a game if it takes <b>timeinMS</b> or more time to advance a single frame <b>frameCount</b> times.
        Timeout limits of more than 60,000 ms will not have an effect since timeouts of more than a minute are counted as crashes.
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings<br>.drawBotNames</td>
	<td>
    	<b>Type:</b> Boolean<br><br>
        Set to <b>true</b> to draw bot names on the game screen.
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings<br>.drawTournamentInfo</td>
	<td>
    	<b>Type:</b> Boolean<br><br>
        Set to <b>true</b> to draw tournament information on the game screen.
    </td>
</tr>
<tr>
	<td>tournamentModuleSettings<br>.drawUnitInfo</td>
	<td>
    	<b>Type:</b> Boolean<br><br>
        Set to <b>true</b> to draw unit information on the game screen.
    </td>
</tr>
</table>

Example server_settings.json:

```json
{
	"bots": [
		{"BotName": "UAlbertaBot", "Race": "Random", "BotType": "proxy", "BWAPIVersion": "BWAPI_420"},
		{"BotName": "ExampleBot", "Race": "Protoss", "BotType": "dll", "BWAPIVersion": "BWAPI_412", "ClientRequirements": [{"Property": "GPU"}]}
	],
	
	"maps": 
	[
		{"mapFile": "maps/aiide/(2)Benzene.scx"},
		{"mapFile": "maps/aiide/(2)Destination.scx"}
	],
	
	"gamesListFile"           : "games.txt",
	"resultsFile"             : "results.txt",
	"detailedResults"         : false,
	"serverPort"              : 1337,
	"clearResults"            : "ask",
	"resumeTournament"        : "ask",
	"startGamesSimultaneously": false,
	"tournamentType"          : "AllVsAll",
	"excludeFromResults"      : [{"BotName": "ExampleBot"}],
	
	"tournamentModuleSettings":
	{
		"localSpeed"    : 0,
		"frameSkip"     : 256,
		"gameFrameLimit": 85714,
		"timeoutLimits" :
		[
			{"timeInMS" : 55,    "frameCount": 320},
			{"timeInMS" : 1000,  "frameCount": 10},
			{"timeInMS" : 10000, "frameCount": 1}
		],
		"drawBotNames"      : true,
		"drawTournamentInfo": true,
		"drawUnitInfo"      : true	
	}
}
```

### Client Settings

<table>
<tr><th>Name</th><th>Value</th></tr>
<tr>
	<td>ClientStarcraftDir</td>
	<td>
    	<b>Type:</b> String<br><br>
    	Directory of Starcraft on client machine; no spaces; end in "\" (backslashes must be escaped).
    </td>
</tr>
<tr>
	<td>DefaultBWAPISettings</td>
	<td>
    	<b>Type:</b> String<br><br>
    	Location of default BWAPI settings file, relative to client.jar; no spaces.
    </td>
</tr>
<tr>
	<td>TournamentModule</td>
	<td>
    	<b>Type:</b> String<br><br>
    	Location of BWAPI Tournament Module DLL, relative to Starcraft directory; no spaces.
    </td>
</tr>
<tr>
	<td>ServerAddress</td>
	<td>
    	<b>Type:</b> String<br><br>
    	IP address and port of server. Example: "192.168.1.100:1337"
    </td>
</tr>
<tr>
	<td>ClientProperties<br>(Optional)</td>
	<td>
    	<b>Type:</b> Array of json objects<br><br>
    	Features of this client that a bot can take advantage of if matched to its <b>ClientRequirements</b> in server settings.
        Each ClientProperty object must contain the following name/value pair:
        <ul>
        <li><b>Property:</b> String</li>
        </ul>
		This array can be empty if you are not using the properties feature.
    </td>
</tr>
</table>

Example client_settings.json:

```json
{
	"ClientStarcraftDir"  : "C:\\TM\\Starcraft\\",
	"DefaultBWAPISettings": "BWAPI.ini",
	"TournamentModule"    : "bwapi-data/TournamentModule.dll",
	"ServerAddress"       : "192.168.1.100:1337",
	"ClientProperties"    : [{"Property": "GPU"}]
}
```

## Change Log

### August 2017

#### Bug Fixes
* Fixed issue where crashes in which the game did not start were not outputting timer stats, causing error reading in results file on server.
* Fixed issue where 1vsALL tournament mode didn't work as intended.

#### New Features
* Added BWAPI 4.2.0 support.
* Allow multiple matches to start simultaneously by ensuring that hosts are always different (enable ONLY for BWAPI 4.2.0 bots).
* ChaosLauncher is no longer used for injecting BWAPI; [injectory](https://github.com/blole/injectory) is used instead.
* Settings files switched to JSON format.
* Added option to give properties to clients, (e.g. "GPU") and requirements to bots. Requirements and properties have to match for a client to run a game.
* Added Results Parser which outputs the results summary and detailed results of a tournament without having to start the server.

#### GUI
* Server GUI displays progress bar.
* Server GUI shows uptime counter.
* Logs include date as well as time.
* Server log can be filtered.
* Added right-click actions for clients: kill, take screenshot, run command, filter.

#### Results output
* Detailed tournament results and summary are now output in JSON format. HTML and CSS have been moved out of the tournament manager output so it is easier to format the results to your liking.
* Added win percentage graph to results output.
* Detailed results now say "unknown" for crashing bot where game didn't start.
* Added filters to Detailed Results Page.
* Added option to exclude a bot from results output in server settings
