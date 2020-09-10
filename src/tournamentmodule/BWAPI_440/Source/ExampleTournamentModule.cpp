#include "ExampleTournamentModule.h"

using namespace BWAPI;

class TournamentModuleState
{

public:

	std::string selfName;
	std::string enemyName;
	std::string mapName;

	int frameCount;
	int selfScore;
	int enemyScore;
	int gameElapsedTime;
	int gameOver;
	int gameTimeUp;

	std::vector<int> timeOutExceeded;

	Timer gameTimer;

	TournamentModuleState()
	{
		selfName		= BWAPI::Broodwar->self()->getName();
		enemyName		= BWAPI::Broodwar->enemy()->getName();
		mapName			= BWAPI::Broodwar->mapName();

		frameCount		= BWAPI::Broodwar->getFrameCount();
		selfScore		= BWAPI::Broodwar->self()->getKillScore() 
						+ BWAPI::Broodwar->self()->getBuildingScore() 
						+ BWAPI::Broodwar->self()->getRazingScore() 
						+ BWAPI::Broodwar->self()->gatheredMinerals()
						+ BWAPI::Broodwar->self()->gatheredGas();

		enemyScore		= 0;

		gameOver = 0;
		gameTimeUp = 0;

		gameTimer.start();

		gameElapsedTime = 0;
	}

	void update(std::vector<int> & times)
	{
		frameCount		= BWAPI::Broodwar->getFrameCount();
		selfScore		= BWAPI::Broodwar->self()->getKillScore() 
						+ BWAPI::Broodwar->self()->getBuildingScore() 
						+ BWAPI::Broodwar->self()->getRazingScore() 
						+ BWAPI::Broodwar->self()->gatheredMinerals()
						+ BWAPI::Broodwar->self()->gatheredGas();

		timeOutExceeded = times;

		gameElapsedTime = (int)gameTimer.getElapsedTimeInMilliSec();
	}

	void ended()
	{
		gameOver = 1;
	}

	bool write(std::string filename)
	{
		gameTimeUp = Broodwar->getFrameCount() > 85714;

		std::ofstream outfile(filename.c_str(), std::ios::out);
		if (outfile.is_open())
		{
			outfile << selfName			<< std::endl;
			outfile << enemyName		<< std::endl;
			outfile << mapName			<< std::endl;
			outfile << frameCount		<< std::endl;
			outfile << selfScore		<< std::endl;
			outfile << enemyScore		<< std::endl;
			outfile << gameElapsedTime  << std::endl;
			outfile << BWAPI::Broodwar->self()->isDefeated()   << std::endl;
			outfile << BWAPI::Broodwar->self()->isVictorious() << std::endl;
			outfile << gameOver		    << std::endl;
			outfile << gameTimeUp       << std::endl;
			
			for (size_t i(0); i<timeOutExceeded.size(); ++i)
			{
				outfile << timeOutExceeded[i] << std::endl;
			}

			return true;
		}
		else
		{
			return false;
		}

		outfile.close();
	}	
};

std::vector<int> timerLimits;
std::vector<int> timerLimitsBound;
std::vector<int> timerLimitsExceeded;

int cameraMoveTime = 48;
int lastMoved = 0;
int localSpeed = 0;
int frameSkip = 0;
int gameTimeLimit = 85714;
int oldFrameCount = -1;
int numPrevEventsThisFrame = 0;

bool drawBotNames = true;
bool drawUnitInfo = true;
bool drawTournamentInfo = true;
bool eventTimesVaried = false;

char buffer[MAX_PATH];

std::vector<int> frameTimes(100000,0);

void ExampleTournamentAI::updateFrameTimers()
{
	const int eventTime = BWAPI::Broodwar->getLastEventTime();
	const int frameCount = BWAPI::Broodwar->getFrameCount();

	// For a client bot, if the TM calls BWAPI v4.4.0's getLastEventTime() it
	// returns the total time for all events for the current frame (not just
	// for the last event), and it returns the same value regardless of which
	// TM callback method (onUnitDiscover(), onFrame() etc) is calling
	// getLastEventTime(). We don't want to count the same amount multiple
	// times. So, we try to detect whether we should interpret the value as
	// the total time for all events for that frame or just the time for the
	// last event, by examining whether getLastEventTime() has ever returned
	// different values during the same frame. For the frames before it is
	// detected, we interpret it as meaning the total time for all events for
	// that frame. Future versions of BWAPI might solve the problem for us,
	// but for v4.4.0 at least, we use this workaround. BWAPI versions before
	// v4.4.0 don't time client bots at all, so the workaround isn't needed
	// in those versions.
	if (frameCount != oldFrameCount)
	{
		frameTimes[frameCount] = eventTime;
		numPrevEventsThisFrame = 1;
		oldFrameCount = frameCount;
	}
	else
	{
		if (eventTimesVaried)
		{
			frameTimes[frameCount] += eventTime;
		}
		else if (eventTime != frameTimes[frameCount])
		{
			eventTimesVaried = true;
			frameTimes[frameCount] = (frameTimes[frameCount] * numPrevEventsThisFrame) + eventTime;
		}

		++numPrevEventsThisFrame;
	}
}

void ExampleTournamentAI::onStart()
{
	
	GetModuleFileName(NULL, buffer, MAX_PATH);
	BWAPI::Broodwar->printf("Path is %s", buffer);

	// Set the command optimization level (reduces high APM, size of bloated replays, etc)
	Broodwar->setCommandOptimizationLevel(DEFAULT_COMMAND_OPTIMIZATION);

	timerLimits.push_back(55);
	timerLimitsBound.push_back(320);
	timerLimits.push_back(1000);
	timerLimitsBound.push_back(10);
	timerLimits.push_back(10000);
	timerLimitsBound.push_back(1);
	timerLimitsExceeded.push_back(0);
	timerLimitsExceeded.push_back(0);
	timerLimitsExceeded.push_back(0);

	parseConfigFile("bwapi-data\\tm_settings.ini");
	
	Broodwar->setLocalSpeed(localSpeed);
	Broodwar->setFrameSkip(frameSkip);

	updateFrameTimers();
}


void ExampleTournamentAI::onEnd(bool isWinner)
{
	TournamentModuleState state = TournamentModuleState();
	state.ended();
	state.update(timerLimitsExceeded);
	state.write("gameState.txt");
}

void ExampleTournamentAI::onFrame()
{
	if ((int)frameTimes.size() < BWAPI::Broodwar->getFrameCount() + 10)
	{
		frameTimes.push_back(0);
	}

	_autoObserver.onFrame();

	if (Broodwar->getFrameCount() % 360 == 0)
	{
		TournamentModuleState state = TournamentModuleState();
		state.update(timerLimitsExceeded);
		state.write("gameState.txt");
	}

	if ((gameTimeLimit > 0) && (Broodwar->getFrameCount() > gameTimeLimit))
	{
		Broodwar->sendText("Game time limit of %d frames reached, exiting", gameTimeLimit);
		Broodwar->leaveGame();
	}
	
	int frame = BWAPI::Broodwar->getFrameCount();
	// check the sum of the times for the previous frame

	if (frame < 10)
	{
		return;
	}

	updateFrameTimers();

	// the total time for the last frame
	int timeElapsed = frameTimes[frame-1];

	// check to see if the timer exceeded any frame time limits
	for (size_t t(0); t<timerLimits.size(); ++t)
	{
		if (timeElapsed > timerLimits[t])
		{
			timerLimitsExceeded[t]++;

			if (timerLimitsExceeded[t] >= timerLimitsBound[t])
			{
				Broodwar->sendText("TIMEOUT on %d ms", timerLimits[t]);
				Broodwar->leaveGame();
			}
		}
	}

	drawTournamentModuleSettings(10, 10);

	if (drawUnitInfo)
	{
		drawUnitInformation(440,6);
	}
}

void ExampleTournamentAI::drawTournamentModuleSettings(int x, int y)
{
	int drawX = x;
	int drawY = y;
	int width = 120;

	if (drawBotNames)
	{
		BWAPI::Broodwar->setTextSize(BWAPI::Text::Size::Huge);
		Broodwar->drawTextScreen(drawX, drawY, "\x07%s \x04vs. \x06%s", BWAPI::Broodwar->self()->getName().c_str(), BWAPI::Broodwar->enemy()->getName().c_str());
		drawY += 18;
		
		BWAPI::Broodwar->setTextSize(BWAPI::Text::Size::Large);
		Broodwar->drawTextScreen(drawX, drawY, "\x03%s", BWAPI::Broodwar->mapFileName().c_str());
		BWAPI::Broodwar->setTextSize();
		drawY += 30;
	}

	/*Broodwar->drawTextScreen(drawX, drawY, "\x04 Player Name:");
	Broodwar->drawTextScreen(drawX+width, drawY, "\x07 %s", BWAPI::Broodwar->self()->getName().c_str());
	drawY += 10;

	Broodwar->drawTextScreen(drawX, drawY, "\x04 Enemy Name:");
	Broodwar->drawTextScreen(drawX+width, drawY, "\x07 %s", BWAPI::Broodwar->enemy()->getName().c_str());
	drawY += 10;

	Broodwar->drawTextScreen(drawX, drawY, "\x04 Map Filename:");
	Broodwar->drawTextScreen(drawX+width, drawY, " %s", BWAPI::Broodwar->mapFileName().c_str());
	drawY += 20;*/

	if (drawTournamentInfo)
	{
		BWAPI::Broodwar->drawTextScreen(drawX, drawY, "\x04 Current Game Time: ");
		BWAPI::Broodwar->drawTextScreen(drawX + width, drawY, " %d", BWAPI::Broodwar->getFrameCount());
		drawY += 10;


		BWAPI::Broodwar->drawTextScreen(drawX, drawY, "\x04 Game Time Limit: ");
		BWAPI::Broodwar->drawTextScreen(drawX + width, drawY, " %d", gameTimeLimit);
		drawY += 10;

		BWAPI::Broodwar->drawTextScreen(drawX, drawY, "\x04 BWAPI Local Speed: ");
		BWAPI::Broodwar->drawTextScreen(drawX + width, drawY, " %d", localSpeed);
		drawY += 10;

		BWAPI::Broodwar->drawTextScreen(drawX, drawY, "\x04 BWAPI Frame Skip: ");
		BWAPI::Broodwar->drawTextScreen(drawX + width, drawY, " %d", frameSkip);
		drawY += 10;

		drawY += 10;
		for (size_t t(0); t<timerLimits.size(); ++t)
		{
			BWAPI::Broodwar->drawTextScreen(drawX, drawY, "\x04 # Frames > %d ms ", timerLimits[t]);
			BWAPI::Broodwar->drawTextScreen(drawX + width, drawY, " %d   (Max %d)", timerLimitsExceeded[t], timerLimitsBound[t]);

			drawY += 10;
		}
	}
	
}

void ExampleTournamentAI::drawUnitInformation(int x, int y) 
{
	
	std::string prefix = "\x04";

	//BWAPI::Broodwar->drawBoxScreen(x-5, y-4, x+200, y+200, BWAPI::Colors::Black, true);

	//BWAPI::Broodwar->drawTextScreen(x, y, "\x04 Unit Information: %s", BWAPI::Broodwar->self()->getRace().getName().c_str());
	BWAPI::Broodwar->drawTextScreen(x, y+20, "\x04%s's Units", BWAPI::Broodwar->self()->getName().c_str());
	BWAPI::Broodwar->drawTextScreen(x+160, y+20, "\x04#");
	BWAPI::Broodwar->drawTextScreen(x+180, y+20, "\x04X");

	int yspace = 0;

	// for each unit in the queue
	for each (BWAPI::UnitType t in BWAPI::UnitTypes::allUnitTypes()) 
	{
		int numUnits = BWAPI::Broodwar->self()->completedUnitCount(t) + BWAPI::Broodwar->self()->incompleteUnitCount(t);
		int numDeadUnits = BWAPI::Broodwar->self()->deadUnitCount(t);

		// if there exist units in the vector
		if (numUnits > 0) 
		{
			if (t.isWorker())			{ prefix = "\x0F"; }
			else if (t.isDetector())	{ prefix = "\x07"; }		
			else if (t.canAttack())		{ prefix = "\x08"; }		
			else if (t.isBuilding())	{ prefix = "\x03"; }
			else						{ prefix = "\x04"; }

			BWAPI::Broodwar->drawTextScreen(x, y+40+((yspace)*10), "%s%s", prefix.c_str(), t.getName().c_str());
			BWAPI::Broodwar->drawTextScreen(x+160, y+40+((yspace)*10), "%s%d", prefix.c_str(), numUnits);
			BWAPI::Broodwar->drawTextScreen(x+180, y+40+((yspace++)*10), "%s%d", prefix.c_str(), numDeadUnits);
		}
	}
}

void ExampleTournamentAI::onSendText(std::string text)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onReceiveText(BWAPI::Player player, std::string text)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onPlayerLeft(BWAPI::Player player)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onNukeDetect(BWAPI::Position target)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitDiscover(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitEvade(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitShow(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitHide(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitCreate(BWAPI::Unit unit)
{
	updateFrameTimers();

	int mult = 3;

	if (BWAPI::Broodwar->getFrameCount() - lastMoved < cameraMoveTime*mult)
	{
		return;
	}

	BWAPI::Broodwar->setScreenPosition(unit->getPosition() - BWAPI::Position(320, 240));
	lastMoved = BWAPI::Broodwar->getFrameCount();
}

void ExampleTournamentAI::onUnitDestroy(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitMorph(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitComplete(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onUnitRenegade(BWAPI::Unit unit)
{
	updateFrameTimers();
}

void ExampleTournamentAI::onSaveGame(std::string gameName)
{
	updateFrameTimers();
}

bool ExampleTournamentModule::onAction(BWAPI::Tournament::ActionID actionType, void *parameter)
{
	switch ( actionType )
	{
		case Tournament::EnableFlag:
			switch ( *(int*)parameter )
			{
				case Flag::CompleteMapInformation:		return false;
				case Flag::UserInput:					return false;
				default:								break;
			}
			// If more flags are added, by default disallow unrecognized flags
			return false;

		case Tournament::PauseGame:						return false;
	//	case Tournament::RestartGame:					return false;
		case Tournament::ResumeGame:					return false;
		case Tournament::SetFrameSkip:					return false;
		case Tournament::SetGUI:						return false;
		case Tournament::SetLocalSpeed:					return false;
		case Tournament::SetMap:						return false;
		case Tournament::LeaveGame:						return true;
	//	case Tournament::ChangeRace:					return false;
		case Tournament::SetLatCom:						return true;
		case Tournament::SetTextSize:					return false;
		case Tournament::SendText:						return false;
		case Tournament::Printf:						return false;
		case Tournament::SetCommandOptimizationLevel:
			return *(int*)parameter >= MINIMUM_COMMAND_OPTIMIZATION;
							
		default:										break;
	}

	return true;
}

void ExampleTournamentModule::onFirstAdvertisement()
{

}


std::vector<std::string> ExampleTournamentAI::getLines(const std::string & filename)
{
    // set up the file
    std::ifstream fin(filename.c_str());
    if (!fin.is_open())
    {
		BWAPI::Broodwar->printf("Tournament Module Settings File Not Found, Using Defaults", filename.c_str());
		return std::vector<std::string>();
    }

	std::string line;

    std::vector<std::string> lines;

    // each line of the file will be a new player to add
    while (fin.good())
    {
        // get the line and set up the string stream
        getline(fin, line);
       
        // skip blank lines and comments
        if (line.length() > 1 && line[0] != '#')
        {
            lines.push_back(line);
        }
    }

	fin.close();

    return lines;
}

void ExampleTournamentAI::parseConfigFile(const std::string & filename)
{
    std::vector<std::string> lines(getLines(filename));

	if (lines.size() > 0)
	{
		timerLimits.clear();
		timerLimitsBound.clear();
		timerLimitsExceeded.clear();
	}

    for (size_t l(0); l<lines.size(); ++l)
    {
        std::istringstream iss(lines[l]);
        std::string option;
        iss >> option;

        if (strcmp(option.c_str(), "LocalSpeed") == 0)
        {
			iss >> localSpeed;
        }
        else if (strcmp(option.c_str(), "FrameSkip") == 0)
        {
            iss >> frameSkip;
        }
        else if (strcmp(option.c_str(), "Timeout") == 0)
        {
            int timeLimit = 0;
			int bound = 0;

			iss >> timeLimit;
			iss >> bound;

			timerLimits.push_back(timeLimit);
			timerLimitsBound.push_back(bound);
			timerLimitsExceeded.push_back(0);
        }
        else if (strcmp(option.c_str(), "GameFrameLimit") == 0)
        {
			iss >> gameTimeLimit;
        }
		else if (strcmp(option.c_str(), "DrawUnitInfo") == 0)
        {
            std::string val;
			iss >> val;
            
			if (strcmp(val.c_str(), "false") == 0)
			{
				drawUnitInfo = false;
			}
        }
		else if (strcmp(option.c_str(), "DrawTournamentInfo") == 0)
        {
            std::string val;
			iss >> val;
            
			if (strcmp(val.c_str(), "false") == 0)
			{
				drawTournamentInfo = false;
			}
        }
		else if (strcmp(option.c_str(), "DrawBotNames") == 0)
        {
            std::string val;
			iss >> val;
            
			if (strcmp(val.c_str(), "false") == 0)
			{
				drawBotNames = false;
			}
        }
		else
		{
			BWAPI::Broodwar->printf("Invalid Option in Tournament Module Settings: %s", option.c_str());
		}
    }
}
