package com.github.muellerma.stopwatch

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.*
import kotlin.time.TimeSource.Monotonic

private const val TIMERANGE: Long = 400
class StopwatchTile : TileService(), CoroutineScope {
    override val coroutineContext = Job()
    private var newMark: Monotonic.ValueTimeMark = Monotonic.markNow()
    private var tapCount: Int = 0

    override fun onClick() {
        Log.d(TAG, "onClick()")
        presetTileState() //for instant feedback
        if (tapCount == 0) {
            preStopRunning() //for time accuracy
        }
        tapWaitOrAct()
    }

    private fun preStopRunning() {
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "preStopRunning(): currentStatus = $currentStatus")
        if (currentStatus is ServiceStatus.Running) {
            StopwatchService.changeState(applicationContext, PlayFlag.PREPAUSE)
        }
    }

    private fun presetTileState() {
        val tile = qsTile ?: return
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "presetTileState(): currentStatus = $currentStatus")
        if (tapCount == 0) {
            //we might run or stop
            when (currentStatus) {
                is ServiceStatus.Stopped -> {
                    tile.state = Tile.STATE_ACTIVE
                }

                is ServiceStatus.Paused -> {
                    tile.state = Tile.STATE_ACTIVE
                }

                is ServiceStatus.Running -> {
                    tile.state = Tile.STATE_INACTIVE
                }
            }
        } else if (tapCount == 1) {
            //we know we gonna reset
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun tapWaitOrAct() {
        launch {
            //add tap count and register new time mark for next tap
            tapCount += 1
            newMark = Monotonic.markNow()
            val lastMark: Monotonic.ValueTimeMark = newMark
            Log.d(TAG, "1- tapCount = $tapCount, newMark = $newMark, lastMark = $lastMark")
            //wait the time range and check if there was another tap since then
            delay(TIMERANGE)
            Log.d(TAG, "2- tapCount = $tapCount, newMark = $newMark, lastMark = $lastMark")
            if (newMark == lastMark) {
                actOnTap()
                //reset tap count for next loop
                tapCount = 0
            }
        }
    }

    private fun actOnTap() {
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "acting on tapCount = $tapCount, currentStatus = $currentStatus")
        if (tapCount == 1) { //pause/start
            if (currentStatus is ServiceStatus.Paused || currentStatus is ServiceStatus.Stopped) {
                StopwatchService.changeState(applicationContext, PlayFlag.PLAY)
            } else {
                StopwatchService.changeState(applicationContext, PlayFlag.PAUSE)
            }
        } else if (tapCount <= 3) { //reset
            if (currentStatus !is ServiceStatus.Stopped) {
                StopwatchService.changeState(applicationContext, PlayFlag.RESET)
            }
        } else { //show history
            return
        }
    }

    override fun onStartListening() {
        Log.d(TAG, "onStartListening()")
        setTileLabel()
        super.onStartListening()
    }

    override fun onTileAdded() {
        Log.d(TAG, "onTileAdded()")
        startTileState()
        setTileLabel()
        super.onTileAdded()
    }

    private fun setTileLabel() {
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "setTileLabel(): running = $currentStatus")
        val tile = qsTile ?: return
        when (currentStatus) {
            is ServiceStatus.Stopped -> { tile.label = getString(R.string.app_name)}
            is ServiceStatus.Paused -> { tile.label = currentStatus.seconds.toFormattedTime()}
            is ServiceStatus.Running -> { tile.label = currentStatus.seconds.toFormattedTime()}
        }
        tile.updateTile()
    }

    private fun startTileState() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
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