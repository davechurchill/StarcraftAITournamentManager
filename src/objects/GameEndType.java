package objects;

import java.io.Serializable;

public enum GameEndType implements Serializable
{
	// These are given in decreasing order of importance. If two clients report different end conditions,
	// then the lower numbered condition (first to appear in list) will be used in the final result.
	
	NO_REPORT,                             // Only one report received; assigned after the fact; should mean the TM client crashed
	GAME_STATE_NOT_UPDATED_60S_BOTH_BOTS,  // gamestate file wasn't updated for 60 seconds (both bots); assigned after the fact; can't know which bot (if any) is responsible
	STARCRAFT_NEVER_DETECTED,              // No game state file (output from TournamentModule once game is running) detected by time limit (60s), and StarCraft never detected running
	GAME_STATE_NEVER_DETECTED,             // No game state file (output from TournamentModule once game is running) detected by time limit (60s), but StarCraft is running
	STARCRAFT_CRASH,                       // Crash detected by TM client, which means Starcraft was running at some point, but then it couldn't find the process. 
	GAME_STATE_NOT_UPDATED_60S,            // gamestate file wasn't updated for 60 seconds, but Starcraft is still running
	NORMAL
}
