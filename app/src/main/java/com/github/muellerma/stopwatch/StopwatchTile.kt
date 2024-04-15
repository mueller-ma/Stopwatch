package com.github.muellerma.stopwatch

import android.content.ComponentName
import android.content.Context
import android.os.Build
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
    private var lastSeconds: Long = 0

    override fun onClick() {
        Log.d(TAG, "onClick()")
        presetTileState() //for instant feedback
        preStopRunning() //for fluidity
        tapWaitOrAct()
    }

    private fun preStopRunning() {
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "preStopRunning(): currentStatus = $currentStatus")
        when (tapCount) {
            0 -> {
                if (currentStatus is ServiceStatus.Running) {
                    StopwatchService.changeState(applicationContext, PlayFlag.PREPAUSE)
                }
            }
            1 -> {
                if (currentStatus is ServiceStatus.Running) {
                    StopwatchService.changeState(applicationContext, PlayFlag.PLAY)
                }
            }
            else -> {}
        }
    }

    private fun presetTileState() {
        val tile = qsTile ?: return
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "presetTileState(): currentStatus = $currentStatus")
        when (tapCount) {
            0 -> { //we might run or stop
                when (currentStatus) {
                    is ServiceStatus.Stopped -> { tile.state = Tile.STATE_ACTIVE }
                    is ServiceStatus.Paused -> { tile.state = Tile.STATE_ACTIVE }
                    is ServiceStatus.Running -> { tile.state = Tile.STATE_INACTIVE }
                }
            }
            1 -> { //we know we gonna reset
                tile.state = Tile.STATE_INACTIVE
            }
            else -> {}
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
            when (tapCount) {
                1 -> { //make sure there isnt a second tap before acting
                    //wait the time range and check if there was another tap since then
                    delay(TIMERANGE)
                    Log.d(TAG, "2- tapCount = $tapCount, newMark = $newMark, lastMark = $lastMark")
                    if (newMark == lastMark) {
                        actOnTap()
                        //reset tap count for next loop
                        tapCount = 0
                    }
                }
                2 -> { //act on the second tap
                    val currentStatus = (application as StopwatchApp).lastStatusUpdate
                    //remember seconds where stopped for subtitle
                    if (currentStatus is ServiceStatus.Paused) {
                        lastSeconds = currentStatus.seconds
                    } else if (currentStatus is ServiceStatus.Running) {
                        lastSeconds = currentStatus.seconds
                    }
                    actOnTap()
                    //reset tap count for next loop
                    delay(TIMERANGE)
                    tapCount = 0
                }
                else -> {}
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
        } else { //reset
            if (currentStatus !is ServiceStatus.Stopped) {
                StopwatchService.changeState(applicationContext, PlayFlag.RESET)
            }
        }
    }

    override fun onStartListening() {
        Log.d(TAG, "onStartListening()")
        setTileLabel()
        super.onStartListening()
    }

    override fun onTileAdded() {
        Log.d(TAG, "onTileAdded()")
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
        setTileLabel()
        super.onTileAdded()
    }

    private fun setTileLabel() {
        val currentStatus = (application as StopwatchApp).lastStatusUpdate
        Log.d(TAG, "setTileLabel(): running = $currentStatus")
        val tile = qsTile ?: return
        when (currentStatus) {
            is ServiceStatus.Stopped -> {
                tile.label = getString(R.string.app_name)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val formattedLastSeconds = lastSeconds.toFormattedTime()
                    tile.subtitle = "Last: $formattedLastSeconds"
                }
            }
            is ServiceStatus.Paused -> {
                tile.label = currentStatus.seconds.toFormattedTime()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Paused"
                }
            }
            is ServiceStatus.Running -> {
                tile.label = currentStatus.seconds.toFormattedTime()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Running"
                }
            }
        }
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