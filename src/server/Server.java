
package server;

import java.io.*;
import java.util.*;
import java.text.*;

import utility.*;
import objects.*;

import java.nio.channels.FileChannel;

public class Server  extends Thread 
{
    private Vector<ServerClientThread> 		clients;							
    private Vector<ServerClientThread> 		free;								
	
    private ServerListenerThread			listener;
	private GameStorage						games;
	
	private int								gameRescheduleTimer = 2000;
	
	public ServerGUI 						gui;
	
	private Game							previousScheduledGame = null;
	
	private static final Server INSTANCE = new Server();

    Server()
	{
    	gui = new ServerGUI(this);
		boolean resumed = gui.handleTournamentResume();
		gui.handleFileDialogues();
    	
		games = GameParser.getGames(ServerSettings.Instance().BotVector, ServerSettings.Instance().MapVector);
		if (resumed)
		{
			ResultsParser rp = new ResultsParser(ServerSettings.Instance().ResultsFile);
			games.removePlayedGames(rp.getGameIDs());
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
		if (!games.hasMoreGames())
		{
			System.err.println("Server: Games list had no valid games in it");
			System.exit(-1);
		}
		
		int neededClients = 2;
		int iterations = 0;
		
		Game nextGame = null;
		
		// keep trying to schedule games
		while (true)
		{
			try
			{		
				// schedule a game once every few seconds
				Thread.sleep(gameRescheduleTimer);
				writeHTMLFiles(iterations++);
				
				if (!games.hasMoreGames())
				{
					if (free.size() == clients.size())
					{
						log("No more games in games list, please shut down tournament!\n");
					}
					else
					{
						log("No more games in games list, please wait for all games to finish.\n");
					}
					continue;
				}
				
				// we can't start a game if we don't have enough clients
				if (free.size() < neededClients) 
				{
					//log(gameString + " Can't start: Not Enough Clients\n");
					continue;
				}
				
				// check if previous games are still starting
				Set<String> startingBots = getStartingHostBotNames();
				
				if (ServerSettings.Instance().StartGamesSimul.equalsIgnoreCase("yes"))
				{
					// we can start multiple games at same time
					nextGame = games.getNextGame(startingBots);
				}
				else if (startingBots.isEmpty())
				{
					// can only start one game at a time, but none others are starting
					nextGame = games.getNextGame();
					if (nextGame == null)
					{
						//all games remaining in this round have host bot already starting match 
						continue;
					}
				}
				else
				{
					// need to wait for current starting game to finish starting
					continue;
				}
				
				String gameString = "Game(" + nextGame.getGameID() + " / " + nextGame.getRound() + ")";
			
                if (previousScheduledGame != null && (nextGame.getRound() > previousScheduledGame.getRound()))
                {
                    // wait until all games from this round are free
                    while (free.size() < clients.size())
                    {
                        log(gameString + " Can't start: Waiting for Previous Round to Finish\n");
                        Thread.sleep(gameRescheduleTimer);
                    }
                    
                    log("Moving Write Directory to Read Directory");
                    
                    // move the write dir to the read dir
                    ServerCommands.Server_MoveWriteToRead();
                }
						
				log(gameString + " SUCCESS: Starting Game\n");
				start1v1Game(nextGame);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				log(e.toString() + "\n");
				continue;
			}
		}
	}
	
	public synchronized void writeHTMLFiles(int iter) throws Exception
	{	
		ResultsParser rp = new ResultsParser(ServerSettings.Instance().ResultsFile);
		
				
		// only write the all results file every 30 reschedules, saves time
		if (ServerSettings.Instance().DetailedResults.equalsIgnoreCase("yes") && iter % 30 == 0)
		{
			log("Generating All Results File...\n");
			rp.writeDetailedResultsJSON();
			log("Generating All Results File Complete!\n");
		}
		
		rp.writeWinPercentageGraph();
		FileUtils.writeToFile(rp.getResultsJSON(), "html/results_summary_json.txt");
	}
	
	public synchronized void updateRunningStats(String client, TournamentModuleState state, boolean isHost)
	{
		int fpm = 24 * 60;
		int fps = 24;
		int minutes = state.frameCount / fpm;
		int seconds = (state.frameCount / fps) % 60;
		gui.UpdateRunningStats(	client, 
								state.selfName, 
								state.enemyName, 
								state.mapName, 
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
		
			gui.UpdateClient(client, status, gameNumber, hostBotName, awayBotName);
		}
	}
	
	public static String getTimeStamp()
	{
		return new SimpleDateFormat("[HH:mm:ss]").format(Calendar.getInstance().getTime());
	}
	
	public synchronized void log(String s)
	{
		gui.logText(getTimeStamp() + " " + s);
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
    
    public synchronized int getNeededClients(Game game) 
	{
		return 2;
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
	
    /**
     * Handles all of the code needed to start a 1v1 game
     */
    private synchronized void start1v1Game(Game game) throws Exception
	{	
    	previousScheduledGame = game;
    	
		// get the clients and their instructions
		ServerClientThread hostClient = free.get(0);
		ServerClientThread awayClient = free.get(1);
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
		hostClient.sendBotFiles(hostInstructions.hostBot);
		
		// send instructions and files to the away machine
		awayClient.sendMessage(awayInstructions);
		awayClient.sendRequiredFiles(awayInstructions.awayBot);
		awayClient.sendBotFiles(awayInstructions.awayBot);
		
		// start games on those machines
		hostClient.sendMessage(new StartGameMessage());
		awayClient.sendMessage(new StartGameMessage());
		
		// set the game to running
		game.setHomeAddress(hostClient.address.toString());
		game.setAwayAddress(awayClient.address.toString());
		game.setStartDate(new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()));
        game.setStatus(GameStatus.RUNNING);
        game.startTime();
		
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

    public synchronized void receiveGameResults(String address, Game game) 
	{
		try 
		{
			log("Recieving Replay: (" + game.getGameID() + " / " + game.getRound() + ")\n");				// EXCEPTION HERE
			System.out.println("Recieving Replay: (" + game.getGameID() + " / " + game.getRound() + ")\n");
			Game g = games.lookupGame(game.getGameID());
			g.updateWithGame(game);
			appendGameData(g);
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
    
    private synchronized void appendGameData(Game game) 
	{
    	System.out.println("Writing out replay data for gameID " + game.getGameID());
        try 
		{
            FileWriter fstream = new FileWriter(ServerSettings.Instance().ResultsFile, true);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(game.getResultString());
            out.close();
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
     	//runCommand("ServerSetup.bat");
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
            if (clients.get(i).getAddress().toString().contentEquals(ip)) 
			{
                System.out.println("Client Found and Stopped\n");
                free.remove(clients.get(i));
                clients.get(i).stopThread();
                clients.remove(i);
                return;
            }
        }
    }
}
class FileCopyThread extends Thread
{
	String source;
	String dest;
	Server server;

	public FileCopyThread(Server m, String source, String dest)
	{
		this.source = source;
		this.dest = dest;
		server = m;
		
		server.log("File Copy Thread Initialized\n");
	}

	public void run()
	{
		server.log("File Copy Thread Started()\n");
	
		while(true)
		{
			try
			{
				Thread.sleep(5000);
				server.log("Trying to copy file to web_docs\n");
				copyFileWindows(source, dest);
				server.log("SUCCESS   : " + source + " copied to " + dest + "\n");
				copyFileWindows(dest, "y:\\web_docs\\index.html");
				server.log("SUCCESS   : Final Copy\n");
			}
			catch (Exception e)
			{
				server.log("FAIL   : " + source + " not copied to " + dest + "\n");
			}
		}
	}

	public void copyFileWindows(String s, String d) throws Exception
	{
		String[] args = { "CMD", "/C", "COPY", "/Y", s, d };
		Process p = Runtime.getRuntime().exec(args);
		p.waitFor();
	}
	
	public void copyFile() throws IOException 
	{
		File sourceFile = new File(source);
		File destFile = new File(dest);
	
		if(!destFile.exists()) 
		{
			destFile.createNewFile();
		}

		FileChannel source = null;
		FileChannel destination = null;
		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;

		try 
		{
			inputStream = new FileInputStream(sourceFile);
			source = inputStream.getChannel();
			outputStream = new FileOutputStream(destFile);
			destination = outputStream.getChannel();
			destination.transferFrom(source, 0, source.size());
			
		}
		finally 
		{
			if (inputStream != null)
			{
				inputStream.close();
			}
			if (source != null) 
			{
				source.close();
			}
			if (outputStream != null)
			{
				outputStream.close();
			}
			if (destination != null) 
			{
				destination.close();
			}
		}
	}
}