package com.github.muellerma.stopwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed, requesting tile state update")
        StopwatchTile.requestTileStateUpdate(context)
    }

    companion object {
        private val TAG = BootReceiver::class.java.simpleName
    }
}