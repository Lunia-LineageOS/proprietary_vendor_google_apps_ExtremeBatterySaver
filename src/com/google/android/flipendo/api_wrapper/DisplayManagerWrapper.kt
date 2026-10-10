package com.google.android.flipendo.api_wrapper

import android.view.Display

interface DisplayManagerWrapper {
    fun disableConnectedDisplay(displayId: Int)

    fun getDisplays(): Array<Display>
}
