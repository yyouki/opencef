package com.opencef.host

import com.opencef.core.BrowserHandle
import com.opencef.core.BrowserInstance
import com.opencef.core.BrowserManager
import com.opencef.core.EventBus

internal class FakeBrowserInstance(
    override val handle: BrowserHandle,
    initialUrl: String,
) : BrowserInstance {

    private val bus = EventBus()

    override var url: String = initialUrl
        private set

    override var isVisible: Boolean = true
        private set

    override var isLoading: Boolean = true
        private set

    override var isDestroyed: Boolean = false
        private set

    val requestedUrls = mutableListOf<String>()
    val emitted = mutableListOf<Pair<String, String>>()

    var reloadCount = 0
        private set

    var showCount = 0
        private set

    var hideCount = 0
        private set

    var destroyCalls = 0
        private set

    override fun loadUrl(url: String) {
        if (isDestroyed) return
        requestedUrls += url
        this.url = url
    }

    override fun reload() {
        if (isDestroyed) return
        reloadCount++
    }

    override fun stopLoading() = Unit

    override fun show() {
        if (isDestroyed) return
        showCount++
        isVisible = true
        bus.onMessage("visibilityChanged", "{\"visible\":true}")
    }

    override fun hide() {
        if (isDestroyed) return
        hideCount++
        isVisible = false
        bus.onMessage("visibilityChanged", "{\"visible\":false}")
    }

    override fun resize(width: Int, height: Int) = Unit

    override fun executeJS(code: String) = Unit

    override fun on(event: String, listener: (payload: String) -> Unit) {
        if (isDestroyed) return
        bus.on(event, listener)
    }

    override fun off(event: String, listener: (payload: String) -> Unit) {
        bus.off(event, listener)
    }

    override fun emit(event: String, payload: String) {
        if (isDestroyed) return
        emitted += event to payload
    }

    override fun destroy() {
        destroyCalls++
        if (isDestroyed) return
        isDestroyed = true
        bus.onMessage("destroyed", "{}")
        bus.clear()
    }

    fun simulatePageEvent(event: String, payload: String) {
        bus.onMessage(event, payload)
    }

    fun simulatePageLoad(pageUrl: String) {
        val payload = "{\"url\":\"$pageUrl\"}"
        url = pageUrl
        isLoading = true
        bus.onMessage("navigation", payload)
        bus.onMessage("loading", payload)
        isLoading = false
        bus.onMessage("loaded", payload)
    }
}

internal class FakeBrowserManager : BrowserManager {

    private var nextId = 0
    private val live = LinkedHashMap<BrowserHandle, FakeBrowserInstance>()

    val created = mutableListOf<FakeBrowserInstance>()

    var failNextCreate = false

    var destroyAllCalls = 0
        private set

    val liveCount: Int
        get() = live.size

    override fun create(url: String): BrowserInstance {
        if (failNextCreate) {
            failNextCreate = false
            throw IllegalStateException("simulated browser creation failure")
        }
        val instance = FakeBrowserInstance(BrowserHandle(nextId++), url)
        live[instance.handle] = instance
        created += instance
        return instance
    }

    override fun destroy(handle: BrowserHandle) {
        live.remove(handle)?.destroy()
    }

    override fun destroyAll() {
        destroyAllCalls++
        live.keys.toList().forEach { destroy(it) }
    }

    override fun get(handle: BrowserHandle): BrowserInstance? = live[handle]
}
