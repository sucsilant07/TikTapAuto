package com.autotapper.tiktok

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.random.Random

class AutoTapAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_START = "com.autotapper.tiktok.START"
        const val ACTION_STOP  = "com.autotapper.tiktok.STOP"
        const val EXTRA_TAP_X          = "tap_x"
        const val EXTRA_TAP_Y          = "tap_y"
        const val EXTRA_INTERVAL       = "interval"
        const val EXTRA_SHARE_INTERVAL = "share_interval"
        const val EXTRA_CHAT_INTERVAL  = "chat_interval"
        const val EXTRA_PHRASES        = "phrases"

        var instance: AutoTapAccessibilityService? = null
        var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())

    private var tapX          = 540f
    private var tapY          = 960f
    private var tapInterval   = 800L
    private var shareInterval = 180_000L
    private var chatInterval  = 120_000L
    private var phrases       = listOf<String>()

    // ── Tap tap continuo ───────────────────────────────────────────────────
    private val tapRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val jx = tapX + Random.nextInt(-25, 26)
            val jy = tapY + Random.nextInt(-25, 26)
            performDoubleTap(jx, jy)
            val jitter = (tapInterval * 0.20).toLong()
            val next   = tapInterval + Random.nextLong(-jitter, jitter + 1)
            handler.postDelayed(this, next.coerceAtLeast(200L))
        }
    }

    // ── Compartir ──────────────────────────────────────────────────────────
    private val shareRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            performShare()
            val jitter = (shareInterval * 0.15).toLong()
            val next   = shareInterval + Random.nextLong(-jitter, jitter + 1)
            handler.postDelayed(this, next.coerceAtLeast(30_000L))
        }
    }

    // ── Mensaje en chat ────────────────────────────────────────────────────
    private val chatRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || phrases.isEmpty()) return
            performChatMessage(phrases[Random.nextInt(phrases.size)])
            val jitter = (chatInterval * 0.15).toLong()
            val next   = chatInterval + Random.nextLong(-jitter, jitter + 1)
            handler.postDelayed(this, next.coerceAtLeast(30_000L))
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_START -> {
                    tapX          = intent.getFloatExtra(EXTRA_TAP_X, 540f)
                    tapY          = intent.getFloatExtra(EXTRA_TAP_Y, 960f)
                    tapInterval   = intent.getLongExtra(EXTRA_INTERVAL, 800L)
                    shareInterval = intent.getLongExtra(EXTRA_SHARE_INTERVAL, 180_000L)
                    chatInterval  = intent.getLongExtra(EXTRA_CHAT_INTERVAL, 120_000L)
                    phrases       = intent.getStringArrayListExtra(EXTRA_PHRASES) ?: emptyList()
                    startActions()
                }
                ACTION_STOP -> stopActions()
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
    override fun onInterrupt() = stopActions()

    override fun onDestroy() {
        super.onDestroy()
        stopActions()
        instance = null
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    // ── Control ────────────────────────────────────────────────────────────

    private fun startActions() {
        isRunning = true
        handler.post(tapRunnable)
        if (shareInterval > 0) {
            handler.postDelayed(shareRunnable, shareInterval)
        }
        if (chatInterval > 0 && phrases.isNotEmpty()) {
            handler.postDelayed(chatRunnable, chatInterval)
        }
    }

    private fun stopActions() {
        isRunning = false
        handler.removeCallbacks(tapRunnable)
        handler.removeCallbacks(shareRunnable)
        handler.removeCallbacks(chatRunnable)
    }

    // ── Doble tap (corazones) ──────────────────────────────────────────────

    private fun performDoubleTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val first = GestureDescription.StrokeDescription(path, 0L, 50L)
        dispatchGesture(
            GestureDescription.Builder().addStroke(first).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (!isRunning) return
                    handler.postDelayed({
                        if (!isRunning) return@postDelayed
                        val second = GestureDescription.StrokeDescription(path, 0L, 50L)
                        dispatchGesture(GestureDescription.Builder().addStroke(second).build(), null, null)
                    }, 120)
                }
            },
            null
        )
    }

    // ── Compartir ──────────────────────────────────────────────────────────

    private fun performShare() {
        val root = rootInActiveWindow ?: return

        // Paso 1: abre el sheet de compartir
        val shareNode = findNodeByKeyword(root, "share")
            ?: findNodeByKeyword(root, "compartir")

        if (shareNode != null) {
            shareNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            val bounds = screenBounds()
            tapAt(bounds.width() * 0.93f, bounds.height() * 0.30f)
        }
        root.recycle()

        // Paso 2: espera que el sheet abra y toca "Copiar enlace"
        handler.postDelayed({
            val sheetRoot = rootInActiveWindow ?: return@postDelayed
            val copyNode = findNodeByKeyword(sheetRoot, "copy link")
                ?: findNodeByKeyword(sheetRoot, "copiar enlace")
                ?: findNodeByKeyword(sheetRoot, "copy")
                ?: findNodeByKeyword(sheetRoot, "copiar")
            if (copyNode != null) {
                copyNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                // Si no encuentra el nodo cierra el sheet sin hacer nada
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            sheetRoot.recycle()
        }, 1200)
    }

    // ── Mensaje en chat ────────────────────────────────────────────────────

    private fun performChatMessage(msg: String) {
        val root = rootInActiveWindow ?: return

        val editNode = findEditableNode(root)
        if (editNode != null) {
            editNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            handler.postDelayed({
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        msg
                    )
                }
                editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                handler.postDelayed({
                    val freshRoot = rootInActiveWindow ?: return@postDelayed
                    val sendNode = findNodeByKeyword(freshRoot, "send")
                        ?: findNodeByKeyword(freshRoot, "enviar")
                        ?: findNodeByKeyword(freshRoot, "publish")
                    if (sendNode != null) {
                        sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        // Fallback: posición típica del botón Enviar en TikTok Live
                        val b = screenBounds()
                        tapAt(b.width() * 0.92f, b.height() * 0.935f)
                    }
                    freshRoot.recycle()
                }, 400)
            }, 600)
        } else {
            // Fallback: toca el área del chat y escribe con gestos de teclado no es posible sin root.
            // Solo se intenta si el AccessibilityService puede encontrar el nodo.
        }

        root.recycle()
    }

    // ── Tap genérico por coordenadas ───────────────────────────────────────

    private fun tapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ── Helpers de árbol de accesibilidad ─────────────────────────────────

    private fun findNodeByKeyword(node: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (desc.contains(keyword) || text.contains(keyword)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByKeyword(child, keyword)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun screenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (getSystemService(WINDOW_SERVICE) as android.view.WindowManager)
                .currentWindowMetrics.bounds
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getMetrics(dm)
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
    }
}
