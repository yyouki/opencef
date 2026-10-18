package com.opencef.host

import com.opencef.core.BrowserHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBrowserHostTest {

    private val manager = FakeBrowserManager()
    private val attachedHandles = mutableListOf<BrowserHandle>()
    private var onMainThread = true

    private val host = AndroidBrowserHost(
        manager = manager,
        attach = { _, _ -> throw AssertionError("container attachment is not exercised in JVM tests") },
        isMainThread = { onMainThread },
    )

    private fun createBrowser(url: String = PAGE_URL): FakeBrowserInstance {
        host.createAndAttach(url) { handle -> attachedHandles += handle }
        return manager.created.last()
    }

    @Test
    fun `createBrowser creates exactly one browser and attaches it once`() {
        val browser = createBrowser()
        assertTrue(host.hasBrowser)
        assertFalse(host.isShutdown)
        assertEquals(1, manager.created.size)
        assertEquals(PAGE_URL, browser.url)
        assertEquals(listOf(browser.handle), attachedHandles)
        assertSame(browser, manager.get(browser.handle))
    }

    @Test
    fun `createBrowser fails while a browser exists and leaves the first one untouched`() {
        val first = createBrowser()
        assertThrows(IllegalStateException::class.java) { createBrowser("file:///android_asset/www/other.html") }
        assertEquals(1, manager.created.size)
        assertEquals(1, manager.liveCount)
        assertFalse(first.isDestroyed)
        assertTrue(host.hasBrowser)
    }

    @Test
    fun `failed creation leaves the host usable without a browser`() {
        manager.failNextCreate = true
        assertThrows(IllegalStateException::class.java) { createBrowser() }
        assertFalse(host.hasBrowser)
        assertFalse(host.isShutdown)
        assertEquals(0, manager.liveCount)
        val browser = createBrowser()
        assertTrue(host.hasBrowser)
        assertFalse(browser.isDestroyed)
    }

    @Test
    fun `failed attachment destroys the half created browser and releases it`() {
        val failure = IllegalArgumentException("attach failed")
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            host.createAndAttach(PAGE_URL) { throw failure }
        }
        assertSame(failure, thrown)
        assertFalse(host.hasBrowser)
        assertEquals(0, manager.liveCount)
        assertTrue(manager.created.single().isDestroyed)
    }

    @Test
    fun `destroyBrowser destroys through the manager and releases the browser`() {
        val browser = createBrowser()
        host.destroyBrowser()
        assertFalse(host.hasBrowser)
        assertTrue(browser.isDestroyed)
        assertEquals(0, manager.liveCount)
        assertEquals(null, manager.get(browser.handle))
        assertFalse(host.isShutdown)
    }

    @Test
    fun `repeated destroyBrowser is a safe no-op`() {
        val browser = createBrowser()
        host.destroyBrowser()
        host.destroyBrowser()
        host.destroyBrowser()
        assertEquals(1, browser.destroyCalls)
        assertFalse(host.hasBrowser)
    }

    @Test
    fun `controls are forwarded to the live browser`() {
        val browser = createBrowser()
        host.reload()
        host.hide()
        host.show()
        host.loadUrl("file:///android_asset/www/next.html")
        host.emit("host_test_result", "{\"ok\":true}")
        assertEquals(1, browser.reloadCount)
        assertEquals(1, browser.hideCount)
        assertEquals(1, browser.showCount)
        assertEquals(listOf("file:///android_asset/www/next.html"), browser.requestedUrls)
        assertEquals(listOf("host_test_result" to "{\"ok\":true}"), browser.emitted)
    }

    @Test
    fun `controls are safe no-ops without a browser`() {
        host.reload()
        host.show()
        host.hide()
        host.loadUrl(PAGE_URL)
        host.emit("host_test_result", "{}")
        host.on("host_test_event") { }
        host.off("host_test_event") { }
        host.destroyBrowser()
        assertFalse(host.hasBrowser)
        assertEquals(0, manager.created.size)
    }

    @Test
    fun `controls are safe no-ops after destroyBrowser`() {
        val browser = createBrowser()
        host.destroyBrowser()
        host.reload()
        host.show()
        host.hide()
        host.loadUrl("file:///android_asset/www/next.html")
        host.emit("host_test_result", "{}")
        assertEquals(0, browser.reloadCount)
        assertEquals(0, browser.showCount)
        assertEquals(0, browser.hideCount)
        assertTrue(browser.requestedUrls.isEmpty())
        assertTrue(browser.emitted.isEmpty())
    }

    @Test
    fun `a new browser can be created after destroyBrowser without inheriting listeners`() {
        val firstEvents = mutableListOf<String>()
        createBrowser()
        host.on("host_test_event") { firstEvents += it }
        host.destroyBrowser()
        val second = createBrowser()
        second.simulatePageEvent("host_test_event", "from second page")
        assertEquals(2, manager.created.size)
        assertTrue(host.hasBrowser)
        assertTrue(firstEvents.isEmpty())
    }

    @Test
    fun `shutdown destroys the live browser and marks the host as shut down`() {
        val browser = createBrowser()
        host.shutdown()
        assertTrue(host.isShutdown)
        assertFalse(host.hasBrowser)
        assertTrue(browser.isDestroyed)
        assertEquals(0, manager.liveCount)
        assertEquals(1, manager.destroyAllCalls)
    }

    @Test
    fun `repeated shutdown is a safe no-op`() {
        val browser = createBrowser()
        host.shutdown()
        host.shutdown()
        host.shutdown()
        assertEquals(1, browser.destroyCalls)
        assertEquals(1, manager.destroyAllCalls)
        assertTrue(host.isShutdown)
    }

    @Test
    fun `shutdown without a browser is safe`() {
        host.shutdown()
        host.shutdown()
        assertTrue(host.isShutdown)
        assertFalse(host.hasBrowser)
    }

    @Test
    fun `createBrowser after shutdown fails and creates nothing`() {
        host.shutdown()
        assertThrows(IllegalStateException::class.java) { createBrowser() }
        assertEquals(0, manager.created.size)
        assertFalse(host.hasBrowser)
        assertTrue(host.isShutdown)
    }

    @Test
    fun `lifecycle calls from a non main thread are rejected without side effects`() {
        val browser = createBrowser()
        onMainThread = false
        assertThrows(IllegalStateException::class.java) { host.destroyBrowser() }
        assertThrows(IllegalStateException::class.java) { host.shutdown() }
        assertThrows(IllegalStateException::class.java) { host.createAndAttach(PAGE_URL) { } }
        assertTrue(host.hasBrowser)
        assertFalse(host.isShutdown)
        assertFalse(browser.isDestroyed)
        assertEquals(1, manager.created.size)
        onMainThread = true
        host.destroyBrowser()
        assertTrue(browser.isDestroyed)
    }

    @Test
    fun `a listener receives each page event exactly once across reloads and navigations`() {
        val browser = createBrowser()
        val received = mutableListOf<String>()
        host.on("host_test_event") { received += it }
        browser.simulatePageEvent("host_test_event", "first")
        host.reload()
        browser.simulatePageLoad(PAGE_URL)
        browser.simulatePageEvent("host_test_event", "second")
        host.loadUrl("file:///android_asset/www/next.html")
        browser.simulatePageLoad("file:///android_asset/www/next.html")
        host.hide()
        host.show()
        browser.simulatePageEvent("host_test_event", "third")
        assertEquals(listOf("first", "second", "third"), received)
    }

    @Test
    fun `off stops delivery to that listener only`() {
        val browser = createBrowser()
        val removed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        val removedListener: (String) -> Unit = { removed += it }
        host.on("host_test_event", removedListener)
        host.on("host_test_event") { kept += it }
        browser.simulatePageEvent("host_test_event", "before")
        host.off("host_test_event", removedListener)
        browser.simulatePageEvent("host_test_event", "after")
        assertEquals(listOf("before"), removed)
        assertEquals(listOf("before", "after"), kept)
    }

    @Test
    fun `a listener registered before any browser exists is ignored`() {
        val received = mutableListOf<String>()
        host.on("host_test_event") { received += it }
        val browser = createBrowser()
        browser.simulatePageEvent("host_test_event", "payload")
        assertTrue(received.isEmpty())
    }

    @Test
    fun `no page events are delivered after destroyBrowser`() {
        val browser = createBrowser()
        val received = mutableListOf<String>()
        host.on("host_test_event") { received += it }
        browser.simulatePageEvent("host_test_event", "alive")
        host.destroyBrowser()
        browser.simulatePageEvent("host_test_event", "late")
        assertEquals(listOf("alive"), received)
    }

    @Test
    fun `the destroyed event is delivered exactly once even when teardown is repeated`() {
        createBrowser()
        var destroyedEvents = 0
        host.on("destroyed") { destroyedEvents++ }
        host.destroyBrowser()
        host.destroyBrowser()
        host.shutdown()
        host.shutdown()
        assertEquals(1, destroyedEvents)
    }

    @Test
    fun `lifecycle events reach host listeners through the same single registration`() {
        val browser = createBrowser()
        val visibility = mutableListOf<String>()
        host.on("visibilityChanged") { visibility += it }
        host.hide()
        host.show()
        browser.simulatePageLoad(PAGE_URL)
        assertEquals(listOf("{\"visible\":false}", "{\"visible\":true}"), visibility)
    }

    private companion object {
        const val PAGE_URL = "file:///android_asset/www/host_demo.html"
    }
}
