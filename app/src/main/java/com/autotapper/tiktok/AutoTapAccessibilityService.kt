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
        const val ACTION_START         = "com.autotapper.tiktok.START"
        const val ACTION_STOP          = "com.autotapper.tiktok.STOP"
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

    /** Delega toda la lógica de cola y prioridad a ActionQueue (clase pura, testeable). */
    private val queue = ActionQueue(
        onExecuteShare = ::executeShare,
        onExecuteChat  = ::executeChat
    )

    // ── Tap tap continuo ───────────────────────────────────────────────────

    private val tapRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (!queue.isBusy) {
                val jx = tapX + Random.nextInt(-25, 26)
                val jy = tapY + Random.nextInt(-25, 26)
                performDoubleTap(jx, jy)
            }
            val jitter = (tapInterval * 0.20).toLong()
            val next   = tapInterval + Random.nextLong(-jitter, jitter + 1)
            handler.postDelayed(this, next.coerceAtLeast(200L))
        }
    }

    // ── Compartir ──────────────────────────────────────────────────────────

    private val shareRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            queue.enqueueShare()
            val jitter = (shareInterval * 0.15).toLong()
            val next   = shareInterval + Random.nextLong(-jitter, jitter + 1)
            handler.postDelayed(this, next.coerceAtLeast(30_000L))
        }
    }

    // ── Mensaje en chat ────────────────────────────────────────────────────

    private val chatRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || phrases.isEmpty()) return
            queue.enqueueChat(phrases[Random.nextInt(phrases.size)])
            val jitter = (chatInterval * 0.15).toLong()
            val next   = chatInterval + Random.nextLong(-jitter, jitter + 1)
            handler.postDelayed(this, next.coerceAtLeast(30_000L))
        }
    }

    // ── Receiver ──────────────────────────────────────────────────────────

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

        // Habilita la recuperación de ventanas en tiempo de ejecución
        // (necesario para que getWindows() devuelva el sheet de compartir)
        serviceInfo = serviceInfo?.also { info ->
            info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

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
        queue.reset()
        handler.post(tapRunnable)
        if (shareInterval > 0)
            handler.postDelayed(shareRunnable, shareInterval)
        if (chatInterval > 0 && phrases.isNotEmpty())
            handler.postDelayed(chatRunnable, chatInterval)
    }

    private fun stopActions() {
        isRunning = false
        queue.reset()
        handler.removeCallbacks(tapRunnable)
        handler.removeCallbacks(shareRunnable)
        handler.removeCallbacks(chatRunnable)
    }

    // ── Doble tap (corazones) ──────────────────────────────────────────────

    private fun performDoubleTap(x: Float, y: Float) {
        val path  = Path().apply { moveTo(x, y) }
        val first = GestureDescription.StrokeDescription(path, 0L, 50L)
        dispatchGesture(
            GestureDescription.Builder().addStroke(first).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (!isRunning || queue.isBusy) return
                    handler.postDelayed({
                        if (!isRunning || queue.isBusy) return@postDelayed
                        val second = GestureDescription.StrokeDescription(path, 0L, 50L)
                        dispatchGesture(
                            GestureDescription.Builder().addStroke(second).build(),
                            null, null
                        )
                    }, 120)
                }
            },
            null
        )
    }

    // ── Ejecutar: Compartir → Copiar enlace ───────────────────────────────
    //
    // Flujo visual observado en capturas:
    //   Botón ↗ compartir → esquina inferior derecha de la barra de chat
    //   Sheet "Compartir" → fila de iconos: Compartir | Copiar enlace | SMS | Email…
    //
    // Llamado solo desde processNext() con isBusy = true ya asignado.
    // Duración total: ~2.3s
    //   0ms    → abre sheet (botón ↗ barra inferior)
    //   1500ms → busca "Copiar enlace" en árbol; si falla usa coordenadas
    //   2300ms → queue.releaseAndNext()
    //
    @Suppress("DEPRECATION")
    private fun executeShare() {
        val b = screenBounds()

        // Paso 1: abrir sheet de compartir
        // Árbol primero; si no, toca el botón ↗ en la barra inferior del Live
        val root      = rootInActiveWindow
        val shareNode = root?.let {
            findNodeByKeyword(it, "share") ?: findNodeByKeyword(it, "compartir")
        }
        if (shareNode != null) {
            shareNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Botón ↗ compartir: esquina inferior derecha de la barra de chat
            tapAt(b.width() * 0.92f, b.height() * 0.94f)
        }
        root?.recycle()

        // Paso 2: tocar "Copiar enlace" una vez abierto el sheet
        handler.postDelayed({
            // Busca en todas las ventanas abiertas (el sheet puede ser una ventana aparte)
            val copyNode = findNodeInAllWindows("copiar enlace")
                ?: findNodeInAllWindows("copy link")
                ?: findNodeInAllWindows("enlace")
                ?: findNodeInAllWindows("copy")
                ?: findNodeInAllWindows("copiar")

            if (copyNode != null) {
                copyNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                // Fallback: posición de "Copiar enlace" en el sheet (2.º icono de la fila)
                // Basado en capturas: ~27 % ancho, ~73 % alto
                tapAt(b.width() * 0.27f, b.height() * 0.73f)
            }

            handler.postDelayed({ queue.releaseAndNext() }, 800)

        }, 1500)
    }

    // ── Ejecutar: Mensaje en chat ──────────────────────────────────────────
    //
    // Flujo visual observado en capturas:
    //   Campo "Escribe algo…" en barra inferior → abre teclado
    //   Botón ➤ Enviar a la derecha del campo de texto
    //
    // Llamado solo desde processNext() con isBusy = true ya asignado.
    // Duración total: ~2.0s
    //   0ms    → activa el campo de chat
    //   700ms  → inyecta texto con ACTION_SET_TEXT
    //   1200ms → toca botón ➤ Enviar
    //   2000ms → queue.releaseAndNext()
    //
    @Suppress("DEPRECATION")
    private fun executeChat(msg: String) {
        val b = screenBounds()

        // Paso 1: activar el campo de chat
        val root     = rootInActiveWindow
        val editNode = root?.let { findEditableNode(it) }
        root?.recycle()

        // Guarda los bounds del EditText ANTES de que el árbol cambie con el teclado
        val editBounds = android.graphics.Rect()
        editNode?.getBoundsInScreen(editBounds)

        if (editNode != null) {
            editNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Fallback: toca la barra "Escribe algo…" en la parte inferior del Live
            tapAt(b.width() * 0.40f, b.height() * 0.94f)
        }

        // Paso 2: inyectar texto
        handler.postDelayed({
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    msg
                )
            }
            // Intenta con el nodo guardado; si está reciclado busca de nuevo
            val targetNode = if (editNode != null && editNode.isEnabled) {
                editNode
            } else {
                rootInActiveWindow?.let { findEditableNode(it) }
            }
            targetNode?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            // Paso 3: tocar el botón ➤ Enviar
            handler.postDelayed({
                // Busca en árbol: "send", "enviar", "publish"
                val sendNode = findNodeInAllWindows("send")
                    ?: findNodeInAllWindows("enviar")
                    ?: findNodeInAllWindows("publish")

                if (sendNode != null) {
                    sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else if (!editBounds.isEmpty) {
                    // Fallback relativo: botón ➤ está a la derecha del EditText, misma altura
                    tapAt(editBounds.right.toFloat() + (b.width() * 0.08f), editBounds.centerY().toFloat())
                } else {
                    // Fallback absoluto: ~93 % ancho, justo encima del teclado (~57 % alto)
                    tapAt(b.width() * 0.93f, b.height() * 0.57f)
                }

                handler.postDelayed({ queue.releaseAndNext() }, 800)

            }, 500)
        }, 700)
    }

    // ── Tap genérico por coordenadas ───────────────────────────────────────

    private fun tapAt(x: Float, y: Float) {
        val path   = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ── Helpers árbol de accesibilidad ─────────────────────────────────────

    /**
     * Busca un nodo por keyword en TODAS las ventanas accesibles.
     * Necesario porque el sheet de compartir de TikTok abre en una ventana separada
     * y rootInActiveWindow solo devuelve la ventana activa superior.
     */
    @Suppress("DEPRECATION")
    private fun findNodeInAllWindows(keyword: String): AccessibilityNodeInfo? {
        val wins = windows ?: return null
        for (window in wins) {
            val root = window.root ?: continue
            val found = findNodeByKeyword(root, keyword)
            root.recycle()
            if (found != null) return found
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun findNodeByKeyword(
        node: AccessibilityNodeInfo,
        keyword: String
    ): AccessibilityNodeInfo? {
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

    @Suppress("DEPRECATION")
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
