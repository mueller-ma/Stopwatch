package com.github.muellerma.stopwatch

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class StopwatchTile : TileService() {
    override fun onClick() {
        Log.d(TAG, "onClick()")
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        StopwatchService.changeState(applicationContext, currentStatus !is ServiceStatus.Running)
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Delaying tile state update for 0.5 seconds")
            setTileState()
        }, 500)
    }

    override fun onStartListening() {
        Log.d(TAG, "onStartListening()")
        setTileState()
        super.onStartListening()
    }

    override fun onTileAdded() {
        Log.d(TAG, "onTileAdded()")
        setTileState()
        super.onTileAdded()
    }

    private fun setTileState() {
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "setTileState(): running = $currentStatus")
        val tile = qsTile ?: return

        val (tileState, tileLabel) = when (currentStatus) {
            is ServiceStatus.Stopped -> Pair(Tile.STATE_INACTIVE, getString(R.string.app_name))
            is ServiceStatus.Running -> Pair(Tile.STATE_ACTIVE, currentStatus.seconds.toFormattedTime())
        }

        tile.apply {
            state = tileState
            label = tileLabel
            updateTile()
        }
    }

    companion object {
        private val TAG = StopwatchTile::class.java.simpleName

        fun requestTileStateUpdate(context: Context) {
            Log.d(TAG, "requestTileStateUpdate()")
            requestListeningState(context, ComponentName(context, StopwatchTile::class.java))
        }
    }
}

fun Long.toFormattedTime(): String {
    val hours = this / 3600
    val minutes = this % 3600 / 60
    val seconds = this % 60

    return if (hours == 0L) {
        String.format("%02d:%02d", minutes, seconds)
    } else {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}