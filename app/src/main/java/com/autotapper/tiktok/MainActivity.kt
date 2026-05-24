package com.autotapper.tiktok

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartStop:    Button
    private lateinit var seekBar:         SeekBar
    private lateinit var tvInterval:      TextView
    private lateinit var tvStatus:        TextView
    private lateinit var tvAccessibility: TextView

    private var isRunning   = false
    private var tapInterval = 800L

    // Recibe el evento cuando el botón flotante detiene el tap
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == FloatingControlService.ACTION_STATUS) {
                isRunning = intent.getBooleanExtra(FloatingControlService.EXTRA_RUNNING, false)
                updateUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStartStop    = findViewById(R.id.btn_start_stop)
        seekBar         = findViewById(R.id.seekbar_interval)
        tvInterval      = findViewById(R.id.tv_interval)
        tvStatus        = findViewById(R.id.tv_status)
        tvAccessibility = findViewById(R.id.tv_accessibility)

        // SeekBar: rango 200ms – 2000ms (pasos de 100ms)
        seekBar.max      = 18
        seekBar.progress = 6   // 800ms por defecto
        updateIntervalLabel()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                tapInterval = (progress + 2) * 100L
                updateIntervalLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnStartStop.setOnClickListener {
            if (isRunning) stopAutoTap() else startAutoTap()
        }

        tvAccessibility.setOnClickListener {
            if (!isAccessibilityEnabled()) openAccessibilitySettings()
        }

        val filter = IntentFilter(FloatingControlService.ACTION_STATUS)
        registerReceiver(statusReceiver, filter, "com.autotapper.tiktok.CONTROL", null)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
    }

    private fun startAutoTap() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Primero activa el servicio de accesibilidad", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero activa el permiso de superposición", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        // Calcula el centro de la pantalla para hacer el tap ahí
        val bounds: Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(m)
            Rect(0, 0, m.widthPixels, m.heightPixels)
        }
        val cx = bounds.width() / 2f
        val cy = bounds.height() / 2f

        // Inicia el AccessibilityService vía broadcast
        sendBroadcast(Intent(AutoTapAccessibilityService.ACTION_START).apply {
            setPackage(packageName)
            putExtra(AutoTapAccessibilityService.EXTRA_TAP_X, cx)
            putExtra(AutoTapAccessibilityService.EXTRA_TAP_Y, cy)
            putExtra(AutoTapAccessibilityService.EXTRA_INTERVAL, tapInterval)
        }, "com.autotapper.tiktok.CONTROL")

        // Inicia el botón flotante
        startService(Intent(this, FloatingControlService::class.java))

        isRunning = true
        updateUI()

        // Abre TikTok automáticamente si está instalado
        val tiktok = packageManager.getLaunchIntentForPackage("com.zhiliaoapp.musically")
            ?: packageManager.getLaunchIntentForPackage("com.ss.android.ugc.trill")
        if (tiktok != null) {
            startActivity(tiktok)
        } else {
            Toast.makeText(this, "Abre TikTok manualmente y ve a un Live", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopAutoTap() {
        sendBroadcast(Intent(AutoTapAccessibilityService.ACTION_STOP).apply { setPackage(packageName) }, "com.autotapper.tiktok.CONTROL")
        stopService(Intent(this, FloatingControlService::class.java))
        isRunning = false
        updateUI()
    }

    private fun updateIntervalLabel() {
        tvInterval.text = "Velocidad: un tap tap cada ${tapInterval}ms"
    }

    private fun updateUI() {
        runOnUiThread {
            if (isRunning) {
                btnStartStop.text = "DETENER TAP TAP"
                btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                tvStatus.text = "ACTIVO — haciendo tap tap automático"
                tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                btnStartStop.text = "INICIAR TAP TAP"
                btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                tvStatus.text = "Inactivo"
                tvStatus.setTextColor(getColor(android.R.color.white))
            }
            if (isAccessibilityEnabled()) {
                tvAccessibility.text = "✓ Servicio de accesibilidad: Activo"
                tvAccessibility.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                tvAccessibility.text = "✗ Accesibilidad INACTIVA — toca aquí para activar"
                tvAccessibility.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.contains(packageName) }
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
