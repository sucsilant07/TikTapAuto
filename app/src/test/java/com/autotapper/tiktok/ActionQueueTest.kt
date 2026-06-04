package com.autotapper.tiktok

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActionQueueTest {

    private val executed = mutableListOf<String>()
    private lateinit var queue: ActionQueue

    @Before
    fun setUp() {
        executed.clear()
        queue = ActionQueue(
            onExecuteShare = { executed.add("share") },
            onExecuteChat  = { msg -> executed.add("chat:$msg") }
        )
    }

    // ── Estado inicial ─────────────────────────────────────────────────────

    @Test
    fun `initial state is idle`() {
        assertFalse(queue.isBusy)
        assertFalse(queue.pendingShare)
        assertNull(queue.pendingChat)
    }

    // ── Ejecución inmediata ────────────────────────────────────────────────

    @Test
    fun `share executes immediately when queue is idle`() {
        queue.enqueueShare()
        assertEquals(listOf("share"), executed)
        assertTrue("debería estar ocupado mientras ejecuta share", queue.isBusy)
        assertFalse(queue.pendingShare)
    }

    @Test
    fun `chat executes immediately when queue is idle`() {
        queue.enqueueChat("hola")
        assertEquals(listOf("chat:hola"), executed)
        assertTrue("debería estar ocupado mientras ejecuta chat", queue.isBusy)
        assertNull(queue.pendingChat)
    }

    // ── Cola cuando está ocupado ───────────────────────────────────────────

    @Test
    fun `share queues when chat is running`() {
        queue.enqueueChat("hola")            // chat inicia
        assertTrue(queue.isBusy)

        queue.enqueueShare()                 // share llega mientras chat corre
        assertTrue(queue.pendingShare)       // queda pendiente
        assertEquals(listOf("chat:hola"), executed) // solo chat corrió
    }

    @Test
    fun `chat queues when share is running`() {
        queue.enqueueShare()                 // share inicia
        assertTrue(queue.isBusy)

        queue.enqueueChat("mensaje")         // chat llega mientras share corre
        assertEquals("mensaje", queue.pendingChat) // queda pendiente
        assertEquals(listOf("share"), executed)    // solo share corrió
    }

    // ── Prioridad: share > chat ────────────────────────────────────────────

    @Test
    fun `share runs before chat when both pending`() {
        // Simula: otra acción está corriendo, share y chat llegan juntos
        queue = ActionQueue(
            onExecuteShare = { executed.add("share") },
            onExecuteChat  = { msg -> executed.add("chat:$msg") }
        )
        // Forzamos estado "ocupado" desde afuera simulando que algo corre
        queue.enqueueChat("primero")         // chat empieza (isBusy=true)
        queue.enqueueShare()                 // share queda pendiente

        // La acción en curso termina
        queue.releaseAndNext()               // share debe ejecutarse antes que nada
        assertEquals("share tiene prioridad sobre chat", "share", executed.last())
        assertTrue(queue.isBusy)
        assertNotNull("chat sigue pendiente", queue.pendingChat)
    }

    @Test
    fun `after share finishes pending chat runs automatically`() {
        queue.enqueueChat("esperando")       // chat empieza
        queue.enqueueShare()                 // share se encola

        queue.releaseAndNext()               // chat termina → share arranca (prioridad)
        assertEquals(listOf("chat:esperando", "share"), executed)

        queue.releaseAndNext()               // share termina → chat pendiente arranca
        assertEquals(listOf("chat:esperando", "share", "chat:esperando"), executed)
    }

    @Test
    fun `after chat finishes pending share runs automatically`() {
        queue.enqueueShare()                 // share empieza
        queue.enqueueChat("luego")           // chat se encola

        queue.releaseAndNext()               // share termina → chat arranca
        assertEquals(listOf("share", "chat:luego"), executed)
        assertTrue(queue.isBusy)
    }

    // ── Descarte de mensajes duplicados ───────────────────────────────────

    @Test
    fun `second chat message is dropped when one already pending`() {
        queue.enqueueShare()                 // share ocupa la cola
        queue.enqueueChat("primero")         // chat se encola
        queue.enqueueChat("segundo")         // debe descartarse

        assertEquals("primero", queue.pendingChat) // "segundo" ignorado
    }

    // ── Reset ──────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        queue.enqueueShare()
        queue.enqueueChat("test")
        queue.reset()

        assertFalse(queue.isBusy)
        assertFalse(queue.pendingShare)
        assertNull(queue.pendingChat)
    }

    @Test
    fun `processNext does nothing after reset`() {
        queue.enqueueShare()
        queue.reset()
        queue.processNext()                  // no debe ejecutar nada

        assertEquals("no debe ejecutar nada post-reset", emptyList<String>(), executed)
    }

    // ── releaseAndNext sin pendientes ─────────────────────────────────────

    @Test
    fun `releaseAndNext with no pending actions just frees mutex`() {
        queue.enqueueShare()                 // share corre → isBusy=true
        queue.releaseAndNext()               // termina, no hay pendientes

        assertFalse(queue.isBusy)
        assertEquals(listOf("share"), executed)
    }
}
