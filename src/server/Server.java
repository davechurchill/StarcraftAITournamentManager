package server;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import utility.*;
import objects.*;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;

public class Server  extends Thread 
{
    private Vector<ServerClientThread> 		clients;							
    private Vector<ServerClientThread> 		free;								
	
    private ServerListenerThread			listener;
	private GameStorage						games;
	
	private int								gameRescheduleTimer = 2000;
	
	public ServerGUI 						gui;
	
	private Game							previousScheduledGame = null;
	private Game							nextGame = null;        
	
	private static final Server INSTANCE = new Server();

    Server()
	{
    	gui = new ServerGUI(this);
    	
    	
		if (ServerSettings.Instance().LadderMode)
		{
			games = new LadderGameStorage();
			try
			{
				gui.updateServerStatus(games.getNumTotalGames(), 0);
			}
			catch (Exception e)
			{
				// couldn't access games storage file
				e.printStackTrace();
			}
		}
		else
		{
			boolean resumed = gui.handleTournamentResume();
			gui.handleFileDialogues();
			
			games = GameParser.getGames();
			
			if (resumed)
			{
				ResultsParser rp = new ResultsParser(ServerSettings.Instance().ResultsFile);
				try
				{
					gui.updateServerStatus(games.getNumTotalGames(), rp.getGameIDs().size());
				}
				catch (Exception e)
				{
					// This shouldn't happen
					e.printStackTrace();
				}
				// This block only executes when not in ladder mode, so the games object
				// will be a TournamentGameStorage, not a LadderGameStorage
				((TournamentGameStorage)games).removePlayedGames(rp.getGameIDs());
			}
		}
		
        clients 	= new Vector<ServerClientThread>();
        free 		= new Vector<ServerClientThread>();

		setupServer();
		
		listener = new ServerListenerThread(this);
		listener.start();
    }
    
    public static Server Instance() 
	{
        return INSTANCE;
    }
    
    public void run()
	{
		if (!ServerSettings.Instance().LadderMode)
		{
			try
			{
				if (!games.hasMoreGames())
				{
					System.err.println("Server: Games list had no valid games in it");
					System.exit(-1);
				}
			}
			catch (Exception e) {
				// This shouldn't happen
				e.printStackTrace();
			}
		}
		
		int neededClients = 2;
		
		nextGame = null;
		
		// only one message of each kind is logged per instance
		boolean notEnoughClients = false;
		boolean requirementsNotMet = false;
		boolean noGamesInGamesList = false;
		
		// keep trying to schedule games
		while (true)
		{
			try
			{
				// schedule a game once every few seconds
				Thread.sleep(gameRescheduleTimer);
				if (ServerSettings.Instance().LadderMode)
				{
					writeServerStatus();
					ServerSettings.Instance().updateSettings();
					gui.updateServerStatus(games.getNumTotalGames(), games.getNumTotalGames() - games.getNumGamesRemaining());
				}
				
				if (!games.hasMoreGames())
				{
					if (ServerSettings.Instance().LadderMode)
					{
						if (!noGamesInGamesList)
						{
							log("Ladder Mode: No games in games list, waiting for new games.\n");
							noGamesInGamesList = true;
						}
					}
					else
					{
						if (free.size() == clients.size())
						{
							log("No more games in games list, please shut down tournament!\n");
							break;
						}
						else
						{
							log("No more games in games list, please wait for all games to finish.\n");
							while (free.size() < clients.size())
		                    {
		                        Thread.sleep(gameRescheduleTimer);
		                    }
						}
					}
					continue;
				}
				noGamesInGamesList = false;
								
				// we can't start a game if we don't have enough clients
				if (free.size() < neededClients) 
				{
					if (!notEnoughClients)
					{
						log("Not enough clients to start next game. Waiting for more free clients.\n");
						notEnoughClients = true;
					}
					continue;
				}
				
				// check if previous games are still starting
				Set<String> startingBots = getStartingHostBotNames();
				
				//get lists of client properties
				Vector<Vector<String>> freeClientProperties = getFreeClientProperties();
				
				if (ServerSettings.Instance().StartGamesSimul)
				{
					if (ServerSettings.Instance().LadderMode)
					{
						updateBotFiles();
					}
					
					// we can start multiple games at same time
					nextGame = games.getNextGame(startingBots, freeClientProperties);
					if (nextGame == null)
					{
						//can't start a game because of bot requirements or round
						if (!requirementsNotMet)
						{
							if (ServerSettings.Instance().EnableBotFileIO)
							{
								log ("Waiting for other games from this round to finish or for clients with needed properties.\n");
							}
							else
							{
								log ("Waiting for clients with needed properties.\n");
							}
							
							requirementsNotMet = true;
						}
						continue;
					}
				}
				else if (startingBots.isEmpty())
				{
					if (ServerSettings.Instance().LadderMode)
					{
						updateBotFiles();
					}
					
					// can only start one game at a time, but none others are starting
					nextGame = games.getNextGame(freeClientProperties);
					if (nextGame == null)
					{
						//can't start a game because of bot requirements or round
						if (!requirementsNotMet)
						{
							if (ServerSettings.Instance().EnableBotFileIO)
							{
								log ("Waiting for other games from this round to finish or for clients with needed properties.\n");
							}
							else
							{
								log ("Waiting for clients with needed properties.\n");
							}
							
							requirementsNotMet = true;
						}
						continue;
					}
				}
				else
				{
					// need to wait for current starting game to finish starting
					continue;
				}
				
				//only wait for round completion and transfer write dir to read dir if Bot File I/O is turned on
				if(ServerSettings.Instance().EnableBotFileIO) {
					
					//check if starting a new round
					if (previousScheduledGame != null && (nextGame.getRound() > previousScheduledGame.getRound()))
	                {
						//check if all games from previous round are finished
						if (free.size() < clients.size())
						{
							log("Next Game: (" + nextGame.getGameID() + " / " + nextGame.getRound() + ") Can't start: Waiting for Previous Round to Finish\n");
						}
						
						// wait until all clients are free
	                    while (free.size() < clients.size())
	                    {
	                        Thread.sleep(gameRescheduleTimer);
	                        if (ServerSettings.Instance().LadderMode)
	        				{
	        					writeServerStatus();
	        				}
	                    }
	                    
	                    log("Moving Write Directory to Read Directory\n");
	                    
	                    // move the write dir to the read dir
	                    ServerCommands.Server_MoveWriteToRead();
	                }
				}
				
				notEnoughClients = false;
				requirementsNotMet = false;
				
				if (ServerSettings.Instance().LadderMode)
				{
					// for ladder mode have to check if bots are valid
					if (checkValidGame(nextGame))
					{
						start1v1Game(nextGame);
					}
				}
				else
				{
					start1v1Game(nextGame);
				}
								
				previousScheduledGame = nextGame;
			}
			catch (Exception e)
			{
				e.printStackTrace();
				log(e.toString() + "\n");
				continue;
			}
		}
	}
	
	public synchronized void updateResults() throws Exception
	{	
		ResultsParser rp = new ResultsParser(ServerSettings.Instance().ResultsFile);
		
		gui.updateServerStatus(games.getNumTotalGames(), rp.getGameIDs().size());
				
		// only write the all results file every 30 reschedules, saves time
		if (ServerSettings.Instance().DetailedResults)
		{
			log("Generating All Results File...\n");
			rp.writeDetailedResults();
			log("Generating All Results File Complete!\n");
		}
		
		rp.writeWinPercentageGraph();
		FileUtils.writeToFile(rp.getResultsSummary(), "html/results/results_summary_json.js", false);
	}
	
	public synchronized void updateRunningStats(String client, TournamentModuleState state, boolean isHost, int gameID)
	{
		int fpm = 24 * 60;
		int fps = 24;
		int minutes = state.frameCount / fpm;
		int seconds = (state.frameCount / fps) % 60;
		String mapFile = games.lookupGame(gameID).getMap().getMapName();
		gui.UpdateRunningStats(	client, 
								state.selfName, 
								state.enemyName, 
								mapFile.substring(mapFile.indexOf(')') + 1, mapFile.indexOf('.')), 
								"" + minutes + ":" + (seconds < 10 ? "0" + seconds : seconds), 
								state.selfWin == 1 ? "Victory" : "");
	}
	
	public synchronized void updateStartingStats(String client, int startingTime)
	{
		gui.UpdateRunningStats(	client, 
								"", 
								"", 
								"", 
								"" + startingTime + "s", 
								"");
	}

	public synchronized void sendScreenshotRequestToClient(String client)
	{
		boolean found = false;
		
		try
		{
			for (int i = 0; i < clients.size(); i++) 
			{
				if (clients.get(i).getAddress().toString().contains(client))
				{
					clients.get(i).sendMessage(new RequestClientScreenshotMessage());
					found = true;
				}
	        }
		}
		catch (Exception ex)
		{
			log("Screenshot Request: Error in searching for client\n");
		}
		
		if (!found)
		{
			log("Screenshot Request: Client not found\n");
		}
	}
		
	public synchronized void sendCommandToAllClients(String command)
	{
		ClientCommandMessage message = new ClientCommandMessage(command);
		
		log("Sending command to all clients: " + message.getCommand() + "\n");
		
		try
    	{
	        for (int i = 0; i < clients.size(); i++) 
			{
	            clients.get(i).sendMessage(message);
	        }
    	}
    	catch (Exception e)
    	{
    		log("There was an error sending the client command message");
    	}
	}
	
	public synchronized void sendCommandToClient(String client, String command)
	{
		ClientCommandMessage message = new ClientCommandMessage(command);
		
		log("Sending command " + message.getCommand() + " to client: " + client + "\n");
		
		try
    	{
	        for (int i = 0; i < clients.size(); i++) 
			{
	        	if (clients.get(i).getAddress().toString().contains(client))
	        	{
	        		clients.get(i).sendMessage(message);
	        	}    
	        }
    	}
    	catch (Exception e)
    	{
    		log("There was an error sending the client command message");
    	}
	}
		
	public synchronized void updateStatusTable()
	{
		for (int i = 0; i < clients.size(); i++) 
		{
			ServerClientThread c = clients.get(i);
			
			updateClientStatus(c);
        }
	}
	
	public synchronized void updateClientGUI(ServerClientThread c)
	{
		if (c != null)
		{
		
			String client = c.toString();
			String status = "" + c.getStatus();
			String gameNumber = "";
			String hostBotName = "";
			String awayBotName = "";
			
			String properties = "";
			if (c.getProperties() != null)
			{
				for (int i = 0; i < c.getProperties().size(); i++)
				{
					properties += c.getProperties().get(i);
					if (i != c.getProperties().size() - 1)
					{
						properties += ", ";
					}
				}
			}
			
			InstructionMessage ins = c.lastInstructionSent;
			
			if (ins != null)
			{
				gameNumber = "" + ins.game_id + " / " + ins.round_id;
				hostBotName = ins.hostBot.getName();
				awayBotName = ins.awayBot.getName();
			}
			
			if (status.equals("READY"))
			{
				gameNumber = "";
				hostBotName = "";
				awayBotName = "";
			}
		
			gui.UpdateClient(client, status, gameNumber, hostBotName, awayBotName, properties);
		}
	}
	
	public synchronized void log(String s)
	{
		gui.logText(ServerGUI.getTimeStamp() + " " + s);
	}
	
	private synchronized void removeNonFreeClientsFromFreeList() 
	{
		for (int i = 0; i < free.size(); i++) 
		{
            if (free.get(i).getStatus() != ClientStatus.READY) 
			{
                free.remove(i);
                log("AddClient(): Non-Free Client in Free List\n");
            }
        }
	}
	
	public synchronized void updateClientStatus(ServerClientThread c)
	{
		if (c != null) 
		{
            if (!clients.contains(c)) 
			{
                clients.add(c);
                log("New Client Added: " + c.toString() + "\n");
                c.sendTournamentModuleSettings();
                try
				{
					c.sendMessage(new InitialSettingsMessage());
				}
                catch (Exception e)
				{
					e.printStackTrace();
					System.exit(-1);
				}
            }
            if (c.getStatus() == ClientStatus.READY && !free.contains(c)) 
			{
                free.add(c);
                log("Client Ready: " + c.toString() + "\n");
            }
            
        }
	}
	 
    public synchronized boolean updateClient(ServerClientThread c) 
	{
		// double check to make sure the free list is correct
        removeNonFreeClientsFromFreeList();
        
		// update this client's status in the list
		updateClientStatus(c);
		updateClientGUI(c);
		//updateStatusTable();
		
        return true;
    }
	
	private synchronized Set<String> getStartingHostBotNames()
	{
		Set<String> hostNames = new HashSet<String>();
		for (int i = 0; i < clients.size(); i++) 
		{
			ServerClientThread c = clients.get(i);
			
			if (c.getStatus() == ClientStatus.STARTING)
			{
				hostNames.add(c.lastInstructionSent.hostBot.getName());
			}
		}
		
		return hostNames;
	}
	
	private Vector<Vector<String>> getFreeClientProperties()
	{
		Vector<Vector<String>> properties = new Vector<Vector<String>>(); 
		
		//properties are null if the client has none
		for (ServerClientThread freeClient: free)
		{
			if (freeClient.getProperties() == null)
			{
				properties.add(new Vector<String>());
			}
			else
			{
				properties.add(freeClient.getProperties());
			}
		}
		return properties;
	}
	
	// this function assumes that there exist two suitable clients for the game
	// that is tested in the GameStorage class
	private int[] getClientsForGame(Game game)
	{
		//{home, away}
		int[] clientsForGame = new int[2];
		
		// sort free clients according to number of properties so that
		// clients with the least number of properties are used first 
		free.sort(new Comparator<ServerClientThread>()
		{
			public int compare(ServerClientThread client1, ServerClientThread client2)
			{
				if (client1.getProperties().size() < client2.getProperties().size())
				{
					return -1;
				}
				else if (client1.getProperties().size() > client2.getProperties().size())
				{
					return 1;
				}
				return 0;
			}
		});
		
		clientsForGame[0] = findClientWithRequirements(free, game.getHomebot().getRequirements(), -1);
		clientsForGame[1] = findClientWithRequirements(free, game.getAwaybot().getRequirements(), clientsForGame[0]);
		
		// in some cases where clients have multiple properties the home bot could have taken the only client
		// acceptable for the away bot, so we have to let away bot pick first
		if (clientsForGame[1] == -1)
		{
			clientsForGame[1] = findClientWithRequirements(free, game.getAwaybot().getRequirements(), -1);
			clientsForGame[0] = findClientWithRequirements(free, game.getHomebot().getRequirements(), clientsForGame[1]);
		}
				
		return clientsForGame;
	}
	
	private int findClientWithRequirements(Vector<ServerClientThread> clients, Vector<String> requirements, int exclude)
	{
		for (int i = 0; i < clients.size(); i++)
		{
			if (exclude == -1 || i != exclude)
			{
				boolean hasAllProperties = true;
				for (String requirement : requirements)
				{
					//check for negated properties
					if (requirement.startsWith("!"))
					{
						if (clients.get(i).getProperties().contains(requirement.substring(1, requirement.length())))
						{
							hasAllProperties = false;
							break;
						}
					}
					//check for required properties
					else if (!clients.get(i).getProperties().contains(requirement))
					{
						hasAllProperties = false;
						break;
					}
				}
				if (hasAllProperties)
				{
					return i;
				}
			}
		}
		return -1;
	}
	
	/**
	 * In Ladder mode, instead of starting a game, output results showing it couldn't be played because of bot issues if needed
	 * @throws Exception 
	 */
	private boolean checkValidGame(Game game) throws Exception
	{
		if (!game.getHomebot().isValid() || !game.getAwaybot().isValid())
		{
			games.removeGame(game.getGameID());
			
			String message = "Unable to play game " + game.getGameID() + ".";
			if (!game.getHomebot().isValid())
			{
				message +=" Bot " + game.getHomebot().getName() + " is not valid.";
			}
			if (!game.getAwaybot().isValid())
			{
				message +=" Bot " + game.getAwaybot().getName() + " is not valid.";
			}
			log(message + "\n");
	        
			try 
			{
	        	JsonObject gameIncomplete = new JsonObject();
	        	gameIncomplete.add("gameID", game.getGameID());
	        	gameIncomplete.add("bots", new JsonArray().add(game.getHomebot().getName()).add(game.getAwaybot().getName()));
	        	JsonArray invalidBots = new JsonArray();
	        	if (!game.getHomebot().isValid())
	        	{
	        		invalidBots.add(game.getHomebot().getName());
	        	}
	        	if (!game.getAwaybot().isValid())
	        	{
	        		invalidBots.add(game.getAwaybot().getName());
	        	}
	        	gameIncomplete.add("invalidBots", invalidBots);
	        	
	        	FileUtils.lockFile(ServerSettings.Instance().ResultsFile + ".lock", 10, 100, 60000);
	        	FileWriter fstream = new FileWriter(ServerSettings.Instance().ResultsFile, true);
	            BufferedWriter out = new BufferedWriter(fstream);
	            out.write(gameIncomplete.toString() + "\n");
	            out.close();
	    		FileUtils.unlockFile(ServerSettings.Instance().ResultsFile + ".lock");
	        } 
			catch (Exception e) 
			{
				e.printStackTrace();
				
	        }
			return false;
		}
		return true;
	}
	
    /**
     * Handles all of the code needed to start a 1v1 game
     */
    private synchronized void start1v1Game(Game game) throws Exception
	{	
    	// get the clients and their instructions
    	int[] gameClients = getClientsForGame(game);
		ServerClientThread hostClient = free.get(gameClients[0]);
		ServerClientThread awayClient = free.get(gameClients[1]);
		InstructionMessage hostInstructions = new InstructionMessage(ServerSettings.Instance().bwapi, true, game);
		InstructionMessage awayInstructions = new InstructionMessage(ServerSettings.Instance().bwapi, false, game);
		
		log("Starting Game: (" + hostInstructions.game_id + " / " + hostInstructions.round_id + ") " 
								  + hostInstructions.hostBot.getName() + " vs. " + hostInstructions.awayBot.getName() + "\n");
		
		// set the clients to starting
        hostClient.setStatus(ClientStatus.STARTING);
		awayClient.setStatus(ClientStatus.STARTING);
		
		// send instructions and files to the host machine
        hostClient.sendMessage(hostInstructions);
		hostClient.sendRequiredFiles(hostInstructions.hostBot);
		hostClient.sendMapFiles();
		hostClient.sendBotFiles(hostInstructions.hostBot);
		
		// send instructions and files to the away machine
		awayClient.sendMessage(awayInstructions);
		awayClient.sendRequiredFiles(awayInstructions.awayBot);
		awayClient.sendMapFiles();
		awayClient.sendBotFiles(awayInstructions.awayBot);
		
		// start games on those machines
		hostClient.sendMessage(new StartGameMessage());
		awayClient.sendMessage(new StartGameMessage());
		
		// set the game to running
		game.setHomeAddress(hostClient.toString());
		game.setAwayAddress(awayClient.toString());
		//game.setStartDate(new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()));
       //game.startTime();
		
		// remove the clients from the free list
        free.remove(hostClient);
        free.remove(awayClient);
		
		updateClientGUI(hostClient);
		updateClientGUI(awayClient);
    }
    
    void shutDown() 
	{
    	try
    	{
	        for (int i = 0; i < clients.size(); i++) 
			{
	            clients.get(i).sendMessage(new ServerShutdownMessage());
	        }
    	}
    	catch (Exception e)
    	{
    		
    	}
    	finally
    	{
    		System.exit(0);
    	}
    }
	
	public void setListener(ServerListenerThread l) 
	{
        listener = l;
    }

    public synchronized void receiveGameResults(String address, GameReport report) 
	{
		try 
		{
			log("Recieving Replay: (" + report.getGameID() + " / " + report.getRound() + ")\n");				// EXCEPTION HERE
			System.out.println("Recieving Replay: (" + report.getGameID() + " / " + report.getRound() + ")\n");
			
			Game g = games.lookupGame(report.getGameID());
			
			report.setAddress((report.isHost() ? g.getHomeAddress() : g.getAwayAddress()));
			report.setOpponentAddress((report.isHost() ? g.getAwayAddress() : g.getHomeAddress()));
			appendGameData(report);
			
			if (!ServerSettings.Instance().LadderMode)
			{
				updateResults();
			}
			else
			{
				((LadderGameStorage)games).receivedResult(g.getGameID());
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			log("Error Receiving Game Results\n");
		}	
    }

    public int getClientIndex(ServerClientThread c)
    {
    	return clients.indexOf(c);
    }
    
    private synchronized void appendGameData(GameReport report) 
	{
    	System.out.println("Writing out replay data for gameID " + report.getGameID());
    	String line = report.getResultJSON(ServerSettings.Instance().tmSettings.TimeoutLimits) + "\n";
    	try
		{
    		if (ServerSettings.Instance().LadderMode)
    		{
    			FileUtils.lockFile(ServerSettings.Instance().ResultsFile + ".lock", 10, 100, 60000);
    			FileUtils.writeToFile(line, ServerSettings.Instance().ResultsFile, true);
    			FileUtils.unlockFile(ServerSettings.Instance().ResultsFile + ".lock");
    		}
    		else
    		{
    			FileUtils.writeToFile(line, ServerSettings.Instance().ResultsFile, true);
    			String crashLog = report.getCrashLog();
    			if (crashLog != null && ServerSettings.Instance().WriteCrashLogs)
    			{
    				new File(ServerSettings.Instance().CrashLogsDir).mkdirs();
    				    				
    				String id = "" + report.getGameID();
    				while (id.length() < 5)
    				{
    					id = "0" + id;
    				}
    				String fileName = ServerSettings.Instance().CrashLogsDir + "/crash_" + report.getReportingBot().getName() + "_" + id + ".txt";
    				FileUtils.writeToFile(crashLog, fileName, false);
    			}
    		}
		}
    	catch (Exception e)
		{
			e.printStackTrace();
		}
    }
    
    
    
    public int getPort() 
	{
        return ServerSettings.Instance().ServerPort;
    }

    public void setupServer() 
	{
		log("Server: Created, Running Setup...\n");
		ServerCommands.Server_InitialSetup();
		log("Server: Setup Successful. Ready!\n");
    }

    synchronized public void removeClient(ServerClientThread c) 
	{
        this.clients.remove(c);
        this.free.remove(c);
		
		gui.RemoveClient(c.toString());
		updateStatusTable();
    }
    
    synchronized public void killClient(String ip) 
	{
        System.out.println("Attempting to kill client: " + ip);
        for (int i = 0; i < clients.size(); i++) 
		{
            if (clients.get(i).getAddress().toString().contains(ip)) 
			{
            	System.out.println("Client Found\n");
                try
				{
					clients.get(i).sendMessage(new ClientShutdownMessage());
					clients.get(i).stopThread();
					free.remove(clients.get(i));
	                	                clients.remove(i);
					System.out.println("Client Found and Stopped\n");
					gui.RemoveClient(ip);
					log("Client removed: " + ip.replaceFirst("^.*/", "") + "\n");
				}
                catch (Exception e)
				{
					e.printStackTrace();
				}
                return;
            }
        }
    }
	
    //write out server status for Ladder
	private void writeServerStatus()
	{
		Vector<Vector<String>> tableData = gui.getTableData();
		JsonObject status = Json.object();
		//JsonObject clients = Json.object();
		JsonArray clients = (JsonArray) Json.array();
		for (Vector<String> vec : tableData)
		{
			JsonObject client = Json.object();
			client.add("Client", vec.get(0));
			client.add("Status", vec.get(1));
			client.add("Game", vec.get(2));			
			client.add("Self", vec.get(3));
			client.add("Enemy", vec.get(4));
			client.add("Map", vec.get(5));
			client.add("Duration", vec.get(6));
			client.add("Win", vec.get(7));
			client.add("Properties", vec.get(8));
			clients.add(client);
		}
		status.add("clients", clients);
		
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(tz);
		String time = df.format(new Date());
		
		status.add("updateTime", time);
		
		try
		{
			status.add("totalGames", games.getNumTotalGames());
			status.add("gamesRemaining", games.getNumGamesRemaining());
		}
		catch (Exception e1)
		{
			//don't update status if can't access all data
			e1.printStackTrace();
			return;
		}
		
		try
		{
			FileUtils.lockFile("server_status.json.lock", 10, 20, 60000);
			FileUtils.writeToFile(status.toString(WriterConfig.PRETTY_PRINT), "server_status.json", false);
			FileUtils.unlockFile("server_status.json.lock");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	//check for new versions of bots for Ladder
	private void updateBotFiles() throws IOException
	{
		boolean foundNewFiles = false;
		
		File botsDir = new File(ServerSettings.Instance().ServerBotDir);
		for (String botDir : botsDir.list())
		{
			//find dirs like "server/bots/new_BotName/"
			if (botDir.length() >= 4 && botDir.substring(0, 4).equals("new_"))
			{
				File newFilesDir = new File(ServerSettings.Instance().ServerBotDir + "/" + botDir);
				if (newFilesDir.isDirectory())
				{
					//obtain lock on this directory and process its contents
					try
					{
						FileUtils.lockFile(ServerSettings.Instance().ServerBotDir + "/" + botDir + ".lock", 4, 50, 60000);
						
						//find all subdirs of "new_BotName/" 
						for (String dirName : newFilesDir.list())
						{
							File dir = new File(ServerSettings.Instance().ServerBotDir + "/" + botDir + "/" + dirName);
							if (!dir.isDirectory())
							{
								continue;
							}
							
							//only looking for 'AI/' or 'read/' dir
							if (!dirName.equals("AI") && !dirName.equals("read"))
							{
								continue;
							}
							
							//expecting a single zip file
							File newFile = null;
							for (String zipFile : dir.list())
							{
								File candidate = new File(ServerSettings.Instance().ServerBotDir + "/" + botDir + "/" + dirName + "/" + zipFile);
								if (candidate.isDirectory())
								{
									continue;
								}
								else if (candidate.getPath().substring(candidate.getPath().length() - 4, candidate.getPath().length()).equalsIgnoreCase(".zip"))
								{
									newFile = candidate;
									break;
								}
							}
							
							if (newFile != null)
							{
								File dest = new File(ServerSettings.Instance().ServerBotDir + "/" + botDir.substring(4) + "/" + dirName);
								if (!dest.exists())
								{
									dest.mkdirs();
								}
								FileUtils.CleanDirectory(dest);
								ZipTools.UnzipFileToDir(newFile, dest);
								foundNewFiles = true;
							}
						}
						
						// delete directory and remove lock
						FileUtils.DeleteDirectory(newFilesDir);
						FileUtils.unlockFile(ServerSettings.Instance().ServerBotDir + "/" + botDir + ".lock");
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}
		}
		if (foundNewFiles)
		{
			//if a bot has been updated in some way, reparse the settings file to check that the bot is valid
			ServerSettings.Instance().updateSettings();
		}
	}
	
	// only used in LadderGameStorage, so only in Ladder mode
	public Vector<Integer> getGamesInProgress()
	{
		Vector<Integer> gameIDs = new Vector<Integer>();
		if (clients != null)
		{
			for (ServerClientThread client : clients)
			{
				if (client.getStatus() != ClientStatus.READY && client.lastInstructionSent != null)
				{
					gameIDs.add(client.lastInstructionSent.game_id);
				}
			}			
		}
		
		// add in the next game to start, since that one is "in progress" in the ladder game storage
		if (nextGame != null)
		{
			gameIDs.add(nextGame.getGameID());
		}
		
		return gameIDs;
	}
	
}