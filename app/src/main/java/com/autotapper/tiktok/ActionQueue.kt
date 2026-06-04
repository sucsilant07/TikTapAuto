package com.autotapper.tiktok

/**
 * Cola de acciones con prioridad para coordinar share y chat
 * sin que se pisen entre sí ni con el tap-tap.
 *
 * Jerarquía: share (1) > chat (2) > tap-tap (cede siempre)
 *
 * Clase pura Kotlin — sin dependencias Android, 100 % testeable.
 */
class ActionQueue(
    private val onExecuteShare: () -> Unit,
    private val onExecuteChat: (String) -> Unit
) {
    @Volatile var isBusy      = false; private set
    @Volatile var pendingShare = false; private set
    @Volatile var pendingChat: String? = null; private set

    /** Encola una acción de compartir. Si la cola está libre la ejecuta ya. */
    fun enqueueShare() {
        pendingShare = true
        processNext()
    }

    /**
     * Encola un mensaje de chat.
     * Si ya hay un mensaje esperando, el nuevo se descarta
     * (no acumular mensajes duplicados entre ciclos).
     */
    fun enqueueChat(msg: String) {
        if (pendingChat == null) pendingChat = msg
        processNext()
    }

    /**
     * Intenta iniciar la siguiente acción pendiente respetando prioridades.
     * Si ya hay algo en curso (isBusy) no hace nada —
     * releaseAndNext() la llamará al terminar la acción actual.
     */
    fun processNext() {
        if (isBusy) return
        when {
            pendingShare -> {
                pendingShare = false
                isBusy = true
                onExecuteShare()
            }
            pendingChat != null -> {
                val msg = pendingChat!!
                pendingChat = null
                isBusy = true
                onExecuteChat(msg)
            }
        }
    }

    /**
     * Llamado al terminar una acción: libera el mutex y
     * dispara la siguiente pendiente si la hay.
     */
    fun releaseAndNext() {
        isBusy = false
        processNext()
    }

    /** Resetea toda la cola (usar en stopActions). */
    fun reset() {
        isBusy       = false
        pendingShare = false
        pendingChat  = null
    }
}
