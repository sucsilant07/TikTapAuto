package com.autotapper.tiktok

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingControlService : Service() {

    companion object {
        const val NOTIF_ID      = 1001
        const val ACTION_STATUS = "com.autotapper.tiktok.STATUS_UPDATE"
        const val EXTRA_RUNNING = "running"
    }

    private enum class State { IDLE, RUNNING }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: android.view.View
    private lateinit var btnFloating: TextView

    private var state = State.IDLE

    // Config recibida desde MainActivity, se envía al AccessibilityService al iniciar
    private var tapX          = 540f
    private var tapY          = 960f
    private var tapInterval   = 800L
    private var shareInterval = 180_000L
    private var chatInterval  = 120_000L
    private var phrases       = arrayListOf<String>()

    private var initX = 0;  private var initY = 0
    private var initTouchX = 0f; private var initTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        showFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            tapX          = it.getFloatExtra(AutoTapAccessibilityService.EXTRA_TAP_X, 540f)
            tapY          = it.getFloatExtra(AutoTapAccessibilityService.EXTRA_TAP_Y, 960f)
            tapInterval   = it.getLongExtra(AutoTapAccessibilityService.EXTRA_INTERVAL, 800L)
            shareInterval = it.getLongExtra(AutoTapAccessibilityService.EXTRA_SHARE_INTERVAL, 180_000L)
            chatInterval  = it.getLongExtra(AutoTapAccessibilityService.EXTRA_CHAT_INTERVAL, 120_000L)
            phrases       = it.getStringArrayListExtra(AutoTapAccessibilityService.EXTRA_PHRASES) ?: arrayListOf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "tiktap_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "TikTap Auto", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("TikTap Auto")
            .setContentText("Toca ▶ cuando estés en el Live para comenzar")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView  = LayoutInflater.from(this).inflate(R.layout.floating_control, null)
        btnFloating   = floatingView.findViewById(R.id.btn_floating)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20; y = 300
        }

        btnFloating.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x;        initY = params.y
                    initTouchX = event.rawX; initTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - initTouchX).toInt()
                    params.y = initY + (event.rawY - initTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - initTouchX) > 10 ||
                                abs(event.rawY - initTouchY) > 10
                    if (!moved) onButtonTapped()
                }
            }
            true
        }

        windowManager.addView(floatingView, params)
        applyIdleStyle()
    }

    private fun onButtonTapped() {
        when (state) {
            State.IDLE    -> beginActions()
            State.RUNNING -> stopEverything()
        }
    }

    private fun beginActions() {
        state = State.RUNNING
        applyRunningStyle()

        sendBroadcast(Intent(AutoTapAccessibilityService.ACTION_START).apply {
            setPackage(packageName)
            putExtra(AutoTapAccessibilityService.EXTRA_TAP_X, tapX)
            putExtra(AutoTapAccessibilityService.EXTRA_TAP_Y, tapY)
            putExtra(AutoTapAccessibilityService.EXTRA_INTERVAL, tapInterval)
            putExtra(AutoTapAccessibilityService.EXTRA_SHARE_INTERVAL, shareInterval)
            putExtra(AutoTapAccessibilityService.EXTRA_CHAT_INTERVAL, chatInterval)
            putStringArrayListExtra(AutoTapAccessibilityService.EXTRA_PHRASES, phrases)
        }, "com.autotapper.tiktok.CONTROL")

        sendStatusBroadcast(running = true)
    }

    private fun stopEverything() {
        sendBroadcast(Intent(AutoTapAccessibilityService.ACTION_STOP).apply {
            setPackage(packageName)
        }, "com.autotapper.tiktok.CONTROL")

        sendStatusBroadcast(running = false)
        stopSelf()
    }

    private fun sendStatusBroadcast(running: Boolean) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_RUNNING, running)
        }, "com.autotapper.tiktok.CONTROL")
    }

    private fun applyIdleStyle() {
        btnFloating.text = "▶"
        btnFloating.setBackgroundResource(R.drawable.circle_green)
        btnFloating.contentDescription = "Iniciar acciones en el Live"
    }

    private fun applyRunningStyle() {
        btnFloating.text = "✕"
        btnFloating.setBackgroundResource(R.drawable.circle_button)
        btnFloating.contentDescription = "Detener acciones"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        }
    }
}
