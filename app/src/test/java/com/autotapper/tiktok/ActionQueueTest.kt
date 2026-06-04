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

    // ── Ejecución inmediata cuando la cola está libre ──────────────────────

    @Test
    fun `share executes immediately when queue is idle`() {
        queue.enqueueShare()
        assertEquals(listOf("share"), executed)
        assertTrue("debe quedar ocupado mientras share corre", queue.isBusy)
        assertFalse(queue.pendingShare)
    }

    @Test
    fun `chat executes immediately when queue is idle`() {
        queue.enqueueChat("hola")
        assertEquals(listOf("chat:hola"), executed)
        assertTrue("debe quedar ocupado mientras chat corre", queue.isBusy)
        assertNull(queue.pendingChat)
    }

    // ── Encolar cuando la cola está ocupada ───────────────────────────────
    //
    // enqueueShare() arranca share inmediatamente (isBusy=true).
    // El siguiente enqueue no puede ejecutar → queda pendiente.

    @Test
    fun `share queues when chat is running`() {
        queue.enqueueChat("hola")            // chat inicia → isBusy=true, pendingChat=null
        assertTrue(queue.isBusy)

        queue.enqueueShare()                 // share llega mientras chat corre → pendiente
        assertTrue(queue.pendingShare)
        assertEquals(listOf("chat:hola"), executed)
    }

    @Test
    fun `chat queues when share is running`() {
        queue.enqueueShare()                 // share inicia → isBusy=true, pendingShare=false
        assertTrue(queue.isBusy)

        queue.enqueueChat("mensaje")         // chat llega mientras share corre → pendiente
        assertEquals("mensaje", queue.pendingChat)
        assertEquals(listOf("share"), executed)
    }

    // ── Prioridad: share > chat ────────────────────────────────────────────
    //
    // Para que ambos queden pendientes a la vez necesitamos que la cola
    // esté ocupada cuando llegan. Arrancamos share, luego encolamos otro
    // share y un chat; al liberar el primero, el segundo share tiene prioridad.

    @Test
    fun `share runs before chat when both pending`() {
        queue.enqueueShare()           // share1 inicia → isBusy=true
        queue.enqueueShare()           // share2 queda pendiente (pendingShare=true)
        queue.enqueueChat("chat1")     // chat queda pendiente (pendingChat="chat1")

        // share1 termina → share2 debe correr antes que chat (prioridad)
        queue.releaseAndNext()
        assertEquals("share tiene prioridad", "share", executed.last())
        assertTrue(queue.isBusy)
        assertEquals("chat sigue pendiente", "chat1", queue.pendingChat)
    }

    // ── Encadenamiento automático al liberar ──────────────────────────────

    @Test
    fun `after share finishes pending chat runs automatically`() {
        queue.enqueueShare()           // share inicia → isBusy=true
        queue.enqueueChat("mensaje")   // chat queda pendiente mientras share corre

        // share termina → chat debe arrancar solo
        queue.releaseAndNext()
        assertEquals(listOf("share", "chat:mensaje"), executed)
        assertTrue("chat debe estar corriendo ahora", queue.isBusy)
    }

    @Test
    fun `after chat finishes pending share runs automatically`() {
        queue.enqueueShare()           // share inicia → isBusy=true
        queue.enqueueShare()           // segundo share queda pendiente
        queue.enqueueChat("luego")     // chat queda pendiente

        // share1 termina → share2 corre (prioridad sobre chat)
        queue.releaseAndNext()
        assertEquals(listOf("share", "share"), executed)

        // share2 termina → chat corre automáticamente
        queue.releaseAndNext()
        assertEquals(listOf("share", "share", "chat:luego"), executed)
        assertTrue(queue.isBusy)
    }

    // ── Descarte de mensajes duplicados ───────────────────────────────────

    @Test
    fun `second chat message is dropped when one already pending`() {
        queue.enqueueShare()                 // share ocupa la cola → isBusy=true
        queue.enqueueChat("primero")         // chat queda pendiente
        queue.enqueueChat("segundo")         // debe descartarse porque ya hay uno

        assertEquals("segundo mensaje ignorado", "primero", queue.pendingChat)
    }

    // ── Reset ──────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all state`() {
        queue.enqueueShare()           // share corre → isBusy=true
        queue.enqueueChat("test")      // chat queda pendiente
        queue.reset()

        assertFalse(queue.isBusy)
        assertFalse(queue.pendingShare)
        assertNull(queue.pendingChat)
    }

    @Test
    fun `processNext does nothing after reset`() {
        // share corre inmediatamente, chat queda pendiente
        queue.enqueueShare()
        queue.enqueueChat("pendiente")

        // reset limpia los pendientes
        queue.reset()
        val sizeAfterReset = executed.size   // solo "share" ejecutó antes del reset

        // processNext no debe ejecutar nada más
        queue.processNext()
        assertEquals("no debe ejecutar nada post-reset", sizeAfterReset, executed.size)
        assertFalse(queue.isBusy)
    }

    // ── releaseAndNext sin pendientes ─────────────────────────────────────

    @Test
    fun `releaseAndNext with no pending actions just frees mutex`() {
        queue.enqueueShare()           // share corre → isBusy=true
        queue.releaseAndNext()         // termina, no hay pendientes

        assertFalse(queue.isBusy)
        assertEquals(listOf("share"), executed)
    }
}
