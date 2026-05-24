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
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: android.view.View

    private var initX = 0;  private var initY = 0
    private var initTouchX = 0f; private var initTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        showFloatingButton()
    }

    private fun buildNotification(): Notification {
        val channelId = "tiktap_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "TikTap Auto", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("TikTap Auto activo")
            .setContentText("Toca el botón rojo flotante para detener")
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView  = LayoutInflater.from(this).inflate(R.layout.floating_control, null)

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

        floatingView.findViewById<ImageButton>(R.id.btn_stop).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x;       initY = params.y
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
                    if (!moved) stopEverything()   // tap = detener
                }
            }
            true
        }

        windowManager.addView(floatingView, params)
    }

    private fun stopEverything() {
        // Detiene el servicio de accesibilidad
        sendBroadcast(Intent(AutoTapAccessibilityService.ACTION_STOP).apply {
            setPackage(packageName)
        }, "com.autotapper.tiktok.CONTROL")
        // Avisa a MainActivity para actualizar el botón
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_RUNNING, false)
        }, "com.autotapper.tiktok.CONTROL")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        }
    }

    companion object {
        const val NOTIF_ID      = 1001
        const val ACTION_STATUS = "com.autotapper.tiktok.STATUS_UPDATE"
        const val EXTRA_RUNNING = "running"
    }
}
