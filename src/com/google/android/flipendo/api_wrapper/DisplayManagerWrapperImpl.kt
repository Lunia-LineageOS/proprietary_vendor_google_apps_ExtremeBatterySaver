package com.google.android.flipendo.api_wrapper

import android.hardware.display.DisplayManager
import android.view.Display

class DisplayManagerWrapperImpl(private val displayManager: DisplayManager) :
    DisplayManagerWrapper {
    override fun disableConnectedDisplay(displayId: Int) {
        displayManager.disableConnectedDisplay(displayId)
    }

    override fun getDisplays(): Array<Display> = displayManager.displays
}
