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
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartStop:    Button
    private lateinit var seekbarInterval: SeekBar
    private lateinit var seekbarShare:    SeekBar
    private lateinit var seekbarChat:     SeekBar
    private lateinit var tvInterval:      TextView
    private lateinit var tvShareInterval: TextView
    private lateinit var tvChatInterval:  TextView
    private lateinit var tvStatus:        TextView
    private lateinit var tvAccessibility: TextView
    private lateinit var etNewPhrase:     EditText
    private lateinit var btnAddPhrase:    Button
    private lateinit var llPhrases:       LinearLayout

    private var isRunning    = false
    private var tapInterval  = 800L
    private var shareInterval = 180_000L   // 3 min
    private var chatInterval  = 120_000L   // 2 min

    private val PREFS_NAME   = "tiktap_prefs"
    private val KEY_PHRASES  = "phrases"

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
        seekbarInterval = findViewById(R.id.seekbar_interval)
        seekbarShare    = findViewById(R.id.seekbar_share)
        seekbarChat     = findViewById(R.id.seekbar_chat)
        tvInterval      = findViewById(R.id.tv_interval)
        tvShareInterval = findViewById(R.id.tv_share_interval)
        tvChatInterval  = findViewById(R.id.tv_chat_interval)
        tvStatus        = findViewById(R.id.tv_status)
        tvAccessibility = findViewById(R.id.tv_accessibility)
        etNewPhrase     = findViewById(R.id.et_new_phrase)
        btnAddPhrase    = findViewById(R.id.btn_add_phrase)
        llPhrases       = findViewById(R.id.ll_phrases)

        setupSeekBars()
        setupPhrases()

        btnStartStop.setOnClickListener {
            if (isRunning) cancelSession() else startSession()
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

    // ── SeekBars ───────────────────────────────────────────────────────────

    private fun setupSeekBars() {
        // Tap tap: 200ms – 2000ms (pasos de 100ms), max=18, default progress=6 → 800ms
        seekbarInterval.max      = 18
        seekbarInterval.progress = 6
        updateTapLabel()
        seekbarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                tapInterval = (p + 2) * 100L
                updateTapLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Compartir: 1–10 min (max=9, progress+1 = minutos), default=2 → 3 min
        seekbarShare.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                shareInterval = (p + 1) * 60_000L
                updateShareLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        updateShareLabel()

        // Chat: 1–10 min, default=1 → 2 min
        seekbarChat.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                chatInterval = (p + 1) * 60_000L
                updateChatLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        updateChatLabel()
    }

    private fun updateTapLabel() {
        tvInterval.text = "Un tap tap cada ${tapInterval} ms"
    }

    private fun updateShareLabel() {
        val min = shareInterval / 60_000L
        tvShareInterval.text = "Compartir cada $min:00 min"
    }

    private fun updateChatLabel() {
        val min = chatInterval / 60_000L
        tvChatInterval.text = "Mensaje cada $min:00 min"
    }

    // ── Frases ─────────────────────────────────────────────────────────────

    private fun setupPhrases() {
        btnAddPhrase.setOnClickListener {
            val text = etNewPhrase.text.toString().trim()
            if (text.isNotEmpty()) {
                addPhrase(text)
                etNewPhrase.text.clear()
            }
        }
        renderPhraseList()
    }

    private fun addPhrase(text: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PHRASES, mutableSetOf())!!.toMutableSet()
        current.add(text)
        prefs.edit().putStringSet(KEY_PHRASES, current).apply()
        renderPhraseList()
    }

    private fun removePhrase(text: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PHRASES, mutableSetOf())!!.toMutableSet()
        current.remove(text)
        prefs.edit().putStringSet(KEY_PHRASES, current).apply()
        renderPhraseList()
    }

    private fun getPhrases(): List<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PHRASES, emptySet())!!.toList()
    }

    private fun renderPhraseList() {
        llPhrases.removeAllViews()
        val phrases = getPhrases()
        if (phrases.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Aún no hay frases. Agrega una arriba."
                textSize = 13f
                setTextColor(0xFF666666.toInt())
                setPadding(0, 4, 0, 4)
            }
            llPhrases.addView(empty)
            return
        }
        phrases.forEach { phrase ->
            val row = LayoutInflater.from(this)
                .inflate(android.R.layout.simple_list_item_1, llPhrases, false) as TextView
            row.text = "• $phrase"
            row.textSize = 13f
            row.setTextColor(0xFFDDDDDD.toInt())
            row.setPadding(0, 6, 0, 6)
            row.setOnLongClickListener {
                removePhrase(phrase)
                Toast.makeText(this, "Frase eliminada", Toast.LENGTH_SHORT).show()
                true
            }
            llPhrases.addView(row)
        }
        val hint = TextView(this).apply {
            text = "Mantén pulsada una frase para eliminarla"
            textSize = 11f
            setTextColor(0xFF555555.toInt())
            setPadding(0, 4, 0, 0)
        }
        llPhrases.addView(hint)
    }

    // ── Inicio / Cancelar sesión ───────────────────────────────────────────

    private fun startSession() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Primero activa el Servicio de accesibilidad", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero activa el permiso de superposición", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }

        val bounds = screenBounds()
        val cx = bounds.width() / 2f
        val cy = bounds.height() / 2f

        // Lanza el botón flotante con toda la config — el tap-tap NO inicia todavía
        startService(Intent(this, FloatingControlService::class.java).apply {
            putExtra(AutoTapAccessibilityService.EXTRA_TAP_X, cx)
            putExtra(AutoTapAccessibilityService.EXTRA_TAP_Y, cy)
            putExtra(AutoTapAccessibilityService.EXTRA_INTERVAL, tapInterval)
            putExtra(AutoTapAccessibilityService.EXTRA_SHARE_INTERVAL, shareInterval)
            putExtra(AutoTapAccessibilityService.EXTRA_CHAT_INTERVAL, chatInterval)
            putStringArrayListExtra(AutoTapAccessibilityService.EXTRA_PHRASES, ArrayList(getPhrases()))
        })

        isRunning = true
        updateUI()

        // Abre TikTok para que el usuario vaya al Live
        val tiktok = packageManager.getLaunchIntentForPackage("com.zhiliaoapp.musically")
            ?: packageManager.getLaunchIntentForPackage("com.ss.android.ugc.trill")
        if (tiktok != null) {
            startActivity(tiktok)
        } else {
            Toast.makeText(this, "Abre TikTok manualmente y ve a un Live", Toast.LENGTH_LONG).show()
        }

        Toast.makeText(this, "Ve al Live y toca el botón ▶ verde para comenzar", Toast.LENGTH_LONG).show()
    }

    private fun cancelSession() {
        sendBroadcast(Intent(AutoTapAccessibilityService.ACTION_STOP).apply {
            setPackage(packageName)
        }, "com.autotapper.tiktok.CONTROL")
        stopService(Intent(this, FloatingControlService::class.java))
        isRunning = false
        updateUI()
    }

    // ── UI ─────────────────────────────────────────────────────────────────

    private fun updateUI() {
        runOnUiThread {
            if (isRunning) {
                btnStartStop.text = "CANCELAR SESIÓN"
                btnStartStop.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_red_dark))
                tvStatus.text = "Sesión activa — toca ▶ en TikTok para iniciar"
                tvStatus.setTextColor(getColor(android.R.color.holo_orange_light))
            } else {
                btnStartStop.text = "INICIAR"
                btnStartStop.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_green_dark))
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

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.contains(packageName) }
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun screenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(m)
            Rect(0, 0, m.widthPixels, m.heightPixels)
        }
    }
}
