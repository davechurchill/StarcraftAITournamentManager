package objects;

import java.io.Serializable;

public enum GameEndType implements Serializable
{
	// These are given in decreasing order of importance. If two clients report different end conditions,
	// then the lower numbered condition (first to appear in list) will be used in the final result.
	NO_STARCRAFT,
	NO_GAME_START,
	STARCRAFT_CRASH,
	NO_ACTIVITY,
	NORMAL
}
