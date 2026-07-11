package com.streambox.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

fun Context.isTelevision(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
