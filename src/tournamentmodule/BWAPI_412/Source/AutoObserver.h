#pragma once

#include "BWAPI.h"

class AutoObserver
{
    int                         _cameraLastMoved;
    int                         _unitFollowFrames;
    BWAPI::UnitInterface *      _observerFollowingUnit;

public:

    AutoObserver();
    void onFrame();
};