package com.autotapper.tiktok

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class AutoTapAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_START = "com.autotapper.tiktok.START"
        const val ACTION_STOP  = "com.autotapper.tiktok.STOP"
        const val EXTRA_TAP_X    = "tap_x"
        const val EXTRA_TAP_Y    = "tap_y"
        const val EXTRA_INTERVAL = "interval"

        var instance: AutoTapAccessibilityService? = null
        var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tapX      = 540f
    private var tapY      = 960f
    private var tapInterval = 800L

    // Ejecuta el doble tap repetidamente mientras isRunning sea true
    private val tapRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                performDoubleTap(tapX, tapY)
                handler.postDelayed(this, tapInterval)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_START -> {
                    tapX        = intent.getFloatExtra(EXTRA_TAP_X, 540f)
                    tapY        = intent.getFloatExtra(EXTRA_TAP_Y, 960f)
                    tapInterval = intent.getLongExtra(EXTRA_INTERVAL, 800L)
                    startTapping()
                }
                ACTION_STOP -> stopTapping()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter().apply {
            addAction(ACTION_START)
            addAction(ACTION_STOP)
        }
        registerReceiver(receiver, filter, "com.autotapper.tiktok.CONTROL", null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() = stopTapping()

    override fun onDestroy() {
        super.onDestroy()
        stopTapping()
        instance = null
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun startTapping() {
        isRunning = true
        handler.post(tapRunnable)
    }

    private fun stopTapping() {
        isRunning = false
        handler.removeCallbacks(tapRunnable)
    }

    // Simula dos toques rápidos en la misma posición (tap tap)
    private fun performDoubleTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }

        val firstTap = GestureDescription.StrokeDescription(path, 0L, 50L)
        dispatchGesture(
            GestureDescription.Builder().addStroke(firstTap).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (!isRunning) return
                    // Segundo toque 120ms después del primero
                    handler.postDelayed({
                        if (!isRunning) return@postDelayed
                        val secondTap = GestureDescription.StrokeDescription(path, 0L, 50L)
                        dispatchGesture(
                            GestureDescription.Builder().addStroke(secondTap).build(),
                            null, null
                        )
                    }, 120)
                }
            },
            null
        )
    }
}
