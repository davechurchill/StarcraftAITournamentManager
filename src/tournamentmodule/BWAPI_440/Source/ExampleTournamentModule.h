#pragma once
#include <BWAPI.h>
#include <vector>
#include <windows.h>
#include <Shlwapi.h>
#include <iostream>
#include <fstream>
#include "Timer.h"
#include <iostream>
#include <cstdlib>
#include <sstream>
#include "AutoObserver.h"

#define DEFAULT_COMMAND_OPTIMIZATION 1
#define MINIMUM_COMMAND_OPTIMIZATION 0

class ExampleTournamentModule : public BWAPI::TournamentModule
{
  virtual bool onAction(BWAPI::Tournament::ActionID actionType, void *parameter = nullptr) override;
  virtual void onFirstAdvertisement() override;
};

class ExampleTournamentAI : public BWAPI::AIModule
{
    AutoObserver _autoObserver;

public:

  virtual void updateFrameTimers();
  virtual void drawUnitInformation(int x, int y);
  virtual void drawTournamentModuleSettings(int x, int y);
  virtual void parseConfigFile(const std::string & filename);
  virtual std::vector<std::string> getLines(const std::string & filename);

  virtual void onStart() override;
  virtual void onEnd(bool isWinner) override;
  virtual void onFrame() override;
  virtual void onSendText(std::string text) override;
  virtual void onReceiveText(BWAPI::Player player, std::string text) override;
  virtual void onPlayerLeft(BWAPI::Player player) override;
  virtual void onNukeDetect(BWAPI::Position target) override;
  virtual void onUnitDiscover(BWAPI::Unit unit) override;
  virtual void onUnitEvade(BWAPI::Unit unit) override;
  virtual void onUnitShow(BWAPI::Unit unit) override;
  virtual void onUnitHide(BWAPI::Unit unit) override;
  virtual void onUnitCreate(BWAPI::Unit unit) override;
  virtual void onUnitDestroy(BWAPI::Unit unit) override;
  virtual void onUnitMorph(BWAPI::Unit unit) override;
  virtual void onUnitComplete(BWAPI::Unit unit) override;
  virtual void onUnitRenegade(BWAPI::Unit unit) override;
  virtual void onSaveGame(std::string gameName) override;

};
