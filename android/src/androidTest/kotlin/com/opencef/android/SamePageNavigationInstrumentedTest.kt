package com.opencef.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.opencef.core.BrowserInstance
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SamePageNavigationInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun repeatedSamePageReloadsReinitializeRuntimeWithoutDuplicateEchoes() {
        val manager = AndroidBrowserManager(context)
        try {
            val readyEvents = LinkedBlockingQueue<String>()
            val loadedCount = AtomicInteger()
            val echoCount = AtomicInteger()
            val echoLatch = CountDownLatch(1)
            val browser = manager.create(TEST_PAGE)

            browser.on("pageReady") { readyEvents.offer(it) }
            browser.on("loaded") { loadedCount.incrementAndGet() }
            browser.on("probeEcho") {
                echoCount.incrementAndGet()
                echoLatch.countDown()
            }

            awaitReady(readyEvents)
            assertFalse(browser.isLoading)

            repeat(2) {
                browser.reload()
                awaitReady(readyEvents)
                assertFalse(browser.isLoading)
            }

            assertTrue("expected loaded after initial page and both reloads", loadedCount.get() >= 3)
            browser.emit("probe", "ping")
            assertTrue("current page did not echo the probe", echoLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            Thread.sleep(150)
            assertEquals("one native event must produce one JS-to-native echo", 1, echoCount.get())
        } finally {
            manager.destroyAll()
        }
    }

    @Test
    fun navigationFromPageAToPageBAndBackRestoresOnlyCurrentPageListeners() {
        val manager = AndroidBrowserManager(context)
        try {
            val pageAReady = LinkedBlockingQueue<String>()
            val loadedUrls = LinkedBlockingQueue<String>()
            val echoCount = AtomicInteger()
            val browser = manager.create(TEST_PAGE)
            browser.on("pageReady") { pageAReady.offer(it) }
            browser.on("loaded") { loadedUrls.offer(it) }
            browser.on("probeEcho") { echoCount.incrementAndGet() }

            awaitReady(pageAReady)
            browser.loadUrl(OTHER_PAGE)
            await(loadedUrls, "page B loaded")

            browser.emit("probe", "page-b-has-no-listener")
            Thread.sleep(150)
            assertEquals("page B should not echo", 0, echoCount.get())

            browser.loadUrl(TEST_PAGE)
            awaitReady(pageAReady)
            browser.emit("probe", "page-a-restored")
            waitUntil("page A echo", TIMEOUT_SECONDS) { echoCount.get() > 0 }
            Thread.sleep(150)
            assertEquals("page A should have one current listener", 1, echoCount.get())
        } finally {
            manager.destroyAll()
        }
    }

    @Test
    fun twoBrowserInstancesKeepEventsIsolated() {
        val manager = AndroidBrowserManager(context)
        try {
            val browserAReady = LinkedBlockingQueue<String>()
            val browserBReady = LinkedBlockingQueue<String>()
            val echoesA = AtomicInteger()
            val echoesB = AtomicInteger()
            val a = manager.create(TEST_PAGE)
            a.on("pageReady") { browserAReady.offer(it) }
            a.on("probeEcho") { echoesA.incrementAndGet() }
            awaitReady(browserAReady)

            val b = manager.create(TEST_PAGE)
            b.on("pageReady") { browserBReady.offer(it) }
            b.on("probeEcho") { echoesB.incrementAndGet() }
            awaitReady(browserBReady)

            a.emit("probe", "only-a")
            waitUntil("browser A echo", TIMEOUT_SECONDS) { echoesA.get() == 1 }
            Thread.sleep(150)
            assertEquals(1, echoesA.get())
            assertEquals(0, echoesB.get())
        } finally {
            manager.destroyAll()
        }
    }

    @Test
    fun destroyedBrowserReceivesNoFurtherEvents() {
        val manager = AndroidBrowserManager(context)
        val browser = manager.create(TEST_PAGE)
        try {
            val ready = LinkedBlockingQueue<String>()
            browser.on("pageReady") { ready.offer(it) }
            awaitReady(ready)

            val echoes = AtomicInteger()
            browser.on("probeEcho") { echoes.incrementAndGet() }
            manager.destroy(browser.handle)

            browser.emit("probe", "after-destroy")
            browser.reload()
            assertTrue(browser.isDestroyed)
            Thread.sleep(150)
            assertEquals(0, echoes.get())
        } finally {
            manager.destroyAll()
        }
    }

    private fun awaitReady(events: LinkedBlockingQueue<String>) {
        await(events, "page JavaScript runtime readiness")
    }

    private fun await(events: LinkedBlockingQueue<String>, label: String): String {
        return checkNotNull(events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Timed out waiting for $label"
        }
    }

    private fun waitUntil(label: String, timeoutSeconds: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("Timed out waiting for $label", condition())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val TEST_PAGE = "file:///android_asset/opencef-test/samepage_test.html"
        const val OTHER_PAGE = "file:///android_asset/opencef-test/samepage_test_other.html"
    }
}
