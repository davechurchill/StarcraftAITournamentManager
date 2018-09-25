package objects;

import java.io.Serializable;

public enum GameEndType implements Serializable
{
	// These are given in decreasing order of importance. If two clients report different end conditions,
	// then the lower numbered condition (first to appear in list) will be used in the final result.
	
	NO_REPORT,          // Only one report received; assigned after the fact; should mean the TM client crashed 
	NO_STARCRAFT,       // No game state file (output from TournamentModule once game is running) detected by time limit (60s), and StarCraft never detected running
	NO_GAME_START,      // No game state file (output from TournamentModule once game is running) detected by time limit (60s), but StarCraft is running
	STARCRAFT_CRASH,    // Crash detected by TM client, which means Starcraft was running at some point, but then it couldn't find the process. 
	NO_ACTIVITY,        // gamestate file wasn't updated for 60 seconds, but Starcraft is still running
	NORMAL
}
