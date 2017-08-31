package client;

import java.util.*;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.text.*;

import javax.imageio.ImageIO;

import objects.*;
import utility.*;

public class Client extends Thread 
{
	protected ClientStatus status;
	
	public ClientSettings settings;
	
	private long startTime;
	private long endTime;
	
	public InstructionMessage previousInstructions;
	
	private Vector<Integer> startingproc;
	
	private ClientListenerThread listener;
	
	private long 		lastGameStateUpdate 	= 0;
	private int 		lastGameStateFrame 		= 0;
	private int 		monitorLoopTimer 		= 1000;
	private int 		gameStartingTimeout 	= 60000;
	private int			timeOutThreshold		= 60000;
	private int 		gameStateReadAttempts 	= 0;
	private boolean 	haveGameStateFile		= false;
	private boolean 	starcraftIsRunning		= false;
	
	public boolean 		shutDown = false;

	public static 		ClientGUI gui;
	
	private DataMessage	requiredFiles			= null;
	private DataMessage	botFiles				= null;
	
	private TournamentModuleSettingsMessage	tmSettings = null;
	
	public Client() 
	{
		gui = new ClientGUI(this);
		
		settings 				= ClientSettings.Instance();
		startTime 				= 0;
		endTime 				= 0;
		startingproc 			= WindowsCommandTools.GetRunningProcesses();
		status 					= ClientStatus.READY;
		previousInstructions 	= null;
				
		ClientCommands.Client_InitialSetup();
	}

	
	public static String getTimeStamp()
	{
		return new SimpleDateFormat("[MMM d, HH:mm:ss]").format(Calendar.getInstance().getTime());
	}
	
	public static synchronized void updateFunc(String func)
	{
		gui.UpdateFunc(func);
	}
	
	private void updateGUI(String currentStatus, String haveState, String scRun, String func, String data)
	{
		String properties = "";
		for (int i = 0; i < ClientSettings.Instance().ClientProperties.size(); i++)
		{
			properties += ClientSettings.Instance().ClientProperties.get(i);
			if (i != ClientSettings.Instance().ClientProperties.size() - 1)
			{
				properties += ",";
			}
			properties += " ";
		}
		
		gui.UpdateClient(listener.getAddress().replace("/", ""), currentStatus, haveGameStateFile ? "STATEFILE" : "NOSTATE", starcraftIsRunning ? "STARCRAFT" : "NOSTARCRAFT", func, data, properties);		
	}
	
	public void setListener(ClientListenerThread l)
	{
		listener = l;
		listener.start();
	}

	public static void log(String s)
	{
		if (gui == null)
		{
			return;
		}
		
		gui.logText(getTimeStamp() + " " + s);
	}
	
	public synchronized void setStatus(ClientStatus s, Game g) 
	{
		System.out.println("\n\nNEW STATUS: " + s + "\n\n");
		this.status = s;
		updateGUI("" + status, haveGameStateFile ? "STATEFILE" : "NOSTATE", starcraftIsRunning ? "STARCRAFT" : "NOSTARCRAFT", "sendStatus", "sendStatus");
		log("Status: " + status + "\n");
		listener.sendMessageToServer(new ClientStatusMessage(this.status, g));
	}
	
	public synchronized void sendRunningUpdate(TournamentModuleState gameState)
	{
		listener.sendMessageToServer(new ClientStatusMessage(this.status, null, gameState, previousInstructions.isHost, 0));
	}
	
	public synchronized void sendStartingUpdate(int startingDuration)
	{
		listener.sendMessageToServer(new ClientStatusMessage(this.status, null, null, previousInstructions.isHost, startingDuration));
	}
	
	public synchronized void setStatus(ClientStatus s) 
	{
		setStatus(s, null);
	}
	
	public synchronized void sendProperties(Vector<String> properties)
	{
		listener.sendMessageToServer(new ClientPropertyMessage(properties));
	}
	
	public void run()
	{
	
		while (true) 
		{	
			TournamentModuleState gameState = new TournamentModuleState();
			// run this loop every so often
			try
			{
				Thread.sleep(monitorLoopTimer);
			}	catch (Exception e) {}
			
			// don't do anything if we haven't connected to the server yet
			if (!listener.connected)
			{
				continue;
			}
			
			// try to read in the game state file
			haveGameStateFile = gameState.readData(settings.ClientStarcraftDir + "gameState.txt");
			starcraftIsRunning = WindowsCommandTools.IsWindowsProcessRunning("StarCraft.exe");
			
			//System.out.println("Client Main Loop: " + status + " " + haveGameStateFile + " " + starcraftIsRunning);
			
			if (status == ClientStatus.READY)
			{	
				updateGUI("" + status, haveGameStateFile ? "STATEFILE" : "NOSTATE", starcraftIsRunning ? "STARCRAFT" : "NOSTARCRAFT", "MainLoop", "WAIT");	
			
				if (haveGameStateFile)
				{
					log("MainLoop: Error, have state file while in main loop\n");
				}
			}
			// if the game is starting, check for game state file
			else if (status == ClientStatus.STARTING)
			{
				// if we we don't have a game state file yet
				if (!haveGameStateFile)
				{
					//System.out.println("MONITOR: No Game State File Yet " );
					gameStateReadAttempts++;
					updateGUI("" + status, haveGameStateFile ? "STATEFILE" : "NOSTATE", starcraftIsRunning ? "STARCRAFT" : "NOSTARCRAFT", "MainLoop", "" + (gameStateReadAttempts * monitorLoopTimer));	
					sendStartingUpdate((gameStateReadAttempts * monitorLoopTimer)/1000);
					
					// if the game didn't start within our threshold time
					if (gameStateReadAttempts > (gameStartingTimeout / monitorLoopTimer))
					{
						log("MainLoop: Game didn't start for " + gameStartingTimeout + "ms\n");
						//System.out.println("MONITOR: I could not read the file in 60 seconds, reporting crash");
						setEndTime();
						prepCrash(gameState);
					}
				}
				else
				{
					// the game is now running
					setStatus(ClientStatus.RUNNING);
				}
			}
			// if the game is currently running
			else if (status == ClientStatus.RUNNING)
			{
				updateGUI("" + status, haveGameStateFile ? "STATEFILE" : "NOSTATE", starcraftIsRunning ? "STARCRAFT" : "NOSTARCRAFT", "MainLoop", "" + gameState.frameCount);
				sendRunningUpdate(gameState);
			
				// update the last frame we read from the gameState file
				if(lastGameStateUpdate == 0 || gameState.frameCount > lastGameStateFrame)
				{
					lastGameStateUpdate = System.currentTimeMillis();
					lastGameStateFrame = gameState.frameCount;
				}
				
				// if the game ended gracefully
				if (gameState.gameEnded == 1) 
				{
					log("MainLoop: Game ended normally, prepping reply\n");
					setEndTime();
					prepReply(gameState);
				}
				// check for a crash
				else
				{
					boolean crash = ((System.currentTimeMillis() - lastGameStateUpdate) > timeOutThreshold)	|| !starcraftIsRunning;
									
					if (crash)
					{
						log("MainLoop: We crashed, prepping crash\n");
						System.out.println("MONITOR: Crash detected, shutting down game");
						setEndTime();
						prepCrash(gameState);
					}
				}
			}
		}
	}
	
	public synchronized void receiveMessage(Message m) 
	{
		if (m instanceof InstructionMessage)
		{
			receiveInstructions((InstructionMessage)m);
		}
		else if (m instanceof DataMessage)
		{
			DataMessage dm = (DataMessage)m;
		
			if (dm.type == DataType.REQUIRED_DIR)
			{
				requiredFiles = dm;
				log("Client: Required files received\n");
			}
			else if (dm.type == DataType.BOT_DIR)
			{
				botFiles = dm;
				log("Client: Bot files received\n");
			}
		}
		else if (m instanceof StartGameMessage)
		{
			if (canStartStarCraft())
			{
				startStarCraft(previousInstructions);
			}
			else
			{
				log("Error: StartGameMessage while StarCraft not ready to start\n");
			}
		}
		else if (m instanceof TournamentModuleSettingsMessage)
		{
			tmSettings = (TournamentModuleSettingsMessage)m;
		}
		else if (m instanceof RequestClientScreenshotMessage)
		{
			try
			{
				Robot robot = new Robot();
			    Rectangle captureSize = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			    BufferedImage bufferedImage = robot.createScreenCapture(captureSize);
			    
			    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			    ImageIO.write(bufferedImage, "png", baos);
			    byte[] bytes = baos.toByteArray();

				listener.sendMessageToServer(new DataMessage(DataType.SCREENSHOT, bytes));
			}
			catch (Exception ex)
			{
				log("Error taking screenshot :(\n");
			}
		}
		else if (m instanceof ClientCommandMessage)
		{
			log("Server told us to execute command: " + ((ClientCommandMessage)m).getCommand() + "\n");
			WindowsCommandTools.RunWindowsCommandAsync(((ClientCommandMessage)m).getCommand());
		}
		else if (m instanceof ServerShutdownMessage || m instanceof ClientShutdownMessage)
		{
			shutDown();
		}
	}
	
	public boolean canStartStarCraft()
	{
		return (previousInstructions != null) && (requiredFiles != null) && (botFiles != null);
	}
	
	public void receiveInstructions(InstructionMessage instructions) 
	{
		System.out.println("Recieved Instructions");
		System.out.println("Game id -> " + instructions.game_id);
		System.out.println(instructions.hostBot.getName() + " vs. " + instructions.awayBot.getName());
		
		previousInstructions = instructions;
	}

	private boolean isProxyBot(InstructionMessage instructions)
	{
		if (instructions == null)
		{
			return false;
		}
		
		if (instructions.isHost)
		{
			return instructions.hostBot.isProxyBot();
		}
		else
		{
			return instructions.awayBot.isProxyBot();
		}
	}

  private boolean isMixedBot(InstructionMessage instructions)
  {
    if (instructions == null)
    {
       return false;
    }

    if (instructions.isHost)
    {
       return instructions.hostBot.isMixedBot();
    }
    else
    {
       return instructions.awayBot.isMixedBot();
    }
  }

	private void startStarCraft(InstructionMessage instructions)
	{
		if (status == ClientStatus.READY)
		{
			setStatus(ClientStatus.STARTING);
			gameStateReadAttempts = 0;
			lastGameStateUpdate = 0;
		
			log("StartStarcraft: Storing Current Processes\n");
			startingproc = WindowsCommandTools.GetRunningProcesses();
			
			log("StartStarcraft: Launching StarCraft\n");
			
			// Prepare the machine for Starcraft launching
			ClientCommands.Client_KillStarcraft();
			ClientCommands.Client_CleanStarcraftDirectory();
			
			// Write the required starcraft files to the client machine
			requiredFiles.write(ClientSettings.Instance().ClientStarcraftDir);
			
			// Write the bot files to the client machine
			botFiles.write(ClientSettings.Instance().ClientStarcraftDir + "bwapi-data");
			
			// Rename the character files to match the bot names
			ClientCommands.Client_RenameCharacterFile(instructions);
			
			// Write out the BWAPI and TournamentModule settings files
			ClientCommands.Client_WriteBWAPISettings(instructions);
			ClientCommands.Client_WriteTournamentModuleSettings(tmSettings);
			
			// Clear out the write directory, since all of it was copied over from server
			ClientCommands.Client_ClearWriteDirectory();
						
			// If the bot requires an executable, start the bot script before
			// StarCraft starts
			if (isProxyBot(previousInstructions) || isMixedBot(previousInstructions))
			{
				ClientCommands.Client_RunBotScript();
			}
			
			// Start starcraft
			ClientCommands.Client_StartStarcraft();

			// Record the time that we tried to start the game
			startTime = System.currentTimeMillis();
			
			// Reset the files for next game
			requiredFiles = null;
			botFiles = null;
		}
		else
		{
			System.out.println("Tried to start StarCraft when not ready");
		}
	}
	
	public String getServer() 
	{
		return settings.ServerAddress;
	}
	
	void prepReply(TournamentModuleState gameState) 
	{
		Game retGame = new Game(	previousInstructions.game_id, 
									previousInstructions.round_id,
									previousInstructions.hostBot, 
									previousInstructions.awayBot, 
									null 
									);
							
		retGame.setWasDraw(gameState.gameHourUp > 0);
		retGame.setFinalFrame(gameState.frameCount);
		
		if (previousInstructions.isHost)
		{
			retGame.setHostTime(getElapsedTime());
			retGame.setHostTimers(gameState.timeOutExceeded);
			retGame.setTimeout(gameState.gameHourUp == 1);
			retGame.setHostwon(gameState.selfWin == 1);
			retGame.setHostScore(gameState.selfScore);
		} 
		else 
		{
			retGame.setGuestTime(getElapsedTime());
			retGame.setAwayTimers(gameState.timeOutExceeded);
			retGame.setTimeout(gameState.gameHourUp == 1);
			retGame.setHostwon(gameState.selfWin == 0);
			retGame.setAwayScore(gameState.selfScore);
		}

		log("Game ended normally. Sending results and cleaning the machine\n");
		setStatus(ClientStatus.SENDING, retGame);
		gameOver();
		setStatus(ClientStatus.READY);
	}

	void prepCrash(TournamentModuleState gameState) 
	{		
		Game retGame = new Game(	previousInstructions.game_id, 
									previousInstructions.round_id,
									previousInstructions.hostBot, 
									previousInstructions.awayBot, 
									null);
		
		retGame.setFinalFrame(gameState.frameCount);
		
		//some crashes leave the timeOuts in gameState empty (if game never started).
		if (gameState.timeOutExceeded.size() == 0)
		{
			Vector<Integer> emptyTimers = new Vector<Integer>();
			for (int i = 0; i < tmSettings.TimeoutLimits.size(); i++) {
				emptyTimers.add(0);
			}
			gameState.timeOutExceeded = emptyTimers;
		}
		
		if (previousInstructions.isHost) 
		{
			retGame.setHostcrash(true);
			retGame.setHostTime(getElapsedTime());
			retGame.setHostTimers(gameState.timeOutExceeded);
			retGame.setHostScore(gameState.selfScore);
		} 
		else 
		{
			retGame.setAwaycrash(true);
			retGame.setGuestTime(getElapsedTime());
			retGame.setAwayTimers(gameState.timeOutExceeded);
			retGame.setAwayScore(gameState.selfScore);
		}
		
		log("Game ended in crash. Sending results and cleaning the machine\n");
		ClientCommands.Client_KillStarcraft();
		ClientCommands.Client_KillExcessWindowsProccess(startingproc);
		setStatus(ClientStatus.SENDING, retGame);
		sendFilesToServer(false);
		ClientCommands.Client_CleanStarcraftDirectory();
		setStatus(ClientStatus.READY);
	}

	private void gameOver()
	{
		sendFilesToServer(true);
		ClientCommands.Client_KillStarcraft();
		ClientCommands.Client_KillExcessWindowsProccess(startingproc);
		ClientCommands.Client_CleanStarcraftDirectory();
	}
	
	public void shutDown()
	{
		ClientCommands.Client_KillStarcraft();
		ClientCommands.Client_KillExcessWindowsProccess(startingproc);
		ClientCommands.Client_KillStarcraft();
		System.exit(0);
	}
	
	private void sendFilesToServer(boolean retryWait)
	{
		// sleep 5 seconds to make sure starcraft wrote the replay file correctly
		
		int attempt=0;
		java.io.File dir;
		boolean fileExists=false;
		do
		{
			try { Thread.sleep(5000); } catch (Exception e) {}
			dir = new java.io.File(ClientSettings.Instance().ClientStarcraftDir + "maps\\replays");
			if(dir.list().length>0)
			{
				java.io.File subDir=dir.listFiles()[0];
				if(subDir.list().length>0)
				{
					fileExists=true;
				}
			}
			attempt++;
		}while(attempt<10 && !fileExists && retryWait);
			
			
		// send the replay data to the server
		DataMessage replayMessage = new DataMessage(DataType.REPLAY, ClientSettings.Instance().ClientStarcraftDir + "maps\\replays");
		log("Sending Data to Sever: " + replayMessage.toString() + "\n");
		listener.sendMessageToServer(replayMessage);
		
		// send the write folder back to the server
		if (previousInstructions != null)
		{
			DataMessage writeDirMessage = new DataMessage(DataType.WRITE_DIR, previousBotName(), ClientSettings.Instance().ClientStarcraftDir + "bwapi-data\\write");
			log("Sending Data to Sever: " + writeDirMessage.toString() + "\n");
			listener.sendMessageToServer(writeDirMessage);
		}
	}
	
	public String previousBotName()
	{
		return previousInstructions.isHost ? previousInstructions.hostBot.getName() : previousInstructions.awayBot.getName();
	}
	
	public void setFinalFrame(String substring) 
	{
		Integer.parseInt(substring.trim());
	}

	public void setEndTime() 
	{
		this.endTime = System.currentTimeMillis();
		System.out.println("Game lasted " + (this.endTime - this.startTime) + " ms");
	}
	
	public long getElapsedTime() 
	{
		return (this.endTime - this.startTime);
	}
}
