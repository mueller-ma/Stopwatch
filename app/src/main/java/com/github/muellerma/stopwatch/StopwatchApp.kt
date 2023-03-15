package com.github.muellerma.stopwatch

import android.app.Application

class StopwatchApp : Application() {
    var observers = mutableListOf<ServiceStatusObserver>()
    var lastStatusUpdate: ServiceStatus = ServiceStatus.Stopped
        private set

    fun notifyObservers(status: ServiceStatus) {
        lastStatusUpdate = status
        observers.forEach { observer ->
            observer.onServiceStatusUpdate(status)
        }
        StopwatchTile.requestTileStateUpdate(this)
    }
}

interface ServiceStatusObserver {
    fun onServiceStatusUpdate(status: ServiceStatus)
}

sealed class ServiceStatus {
    class Running(val seconds: Long) : ServiceStatus() {
        override fun toString() = "${Running::class.java.simpleName}($seconds)"
    }
    object Stopped : ServiceStatus() {
        override fun toString(): String = Stopped::class.java.simpleName
    }
}