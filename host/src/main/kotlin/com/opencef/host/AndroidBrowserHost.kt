package com.opencef.host

import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import com.opencef.android.AndroidBrowserManager
import com.opencef.core.BrowserHandle
import com.opencef.core.BrowserInstance
import com.opencef.core.BrowserManager

internal class AndroidBrowserHost(
    manager: BrowserManager,
    attach: (BrowserHandle, ViewGroup) -> Unit,
    private val isMainThread: () -> Boolean,
) : BrowserHost {

    private class Resources(
        val manager: BrowserManager,
        val attach: (BrowserHandle, ViewGroup) -> Unit,
    )

    @Volatile
    private var resources: Resources? = Resources(manager, attach)

    @Volatile
    private var browser: BrowserInstance? = null

    override val hasBrowser: Boolean
        get() = browser != null

    override val isShutdown: Boolean
        get() = resources == null

    override fun createBrowser(url: String, container: ViewGroup) {
        val active = resourcesForCreation()
        openBrowser(active, url) { handle -> active.attach(handle, container) }
    }

    internal fun createAndAttach(url: String, attach: (BrowserHandle) -> Unit) {
        openBrowser(resourcesForCreation(), url, attach)
    }

    override fun loadUrl(url: String) {
        browser?.loadUrl(url)
    }

    override fun reload() {
        browser?.reload()
    }

    override fun show() {
        browser?.show()
    }

    override fun hide() {
        browser?.hide()
    }

    override fun on(event: String, listener: (payload: String) -> Unit) {
        browser?.on(event, listener)
    }

    override fun off(event: String, listener: (payload: String) -> Unit) {
        browser?.off(event, listener)
    }

    override fun emit(event: String, payload: String) {
        browser?.emit(event, payload)
    }

    override fun destroyBrowser() {
        requireMainThread("destroyBrowser()")
        val current = browser ?: return
        browser = null
        resources?.manager?.destroy(current.handle)
    }

    override fun shutdown() {
        requireMainThread("shutdown()")
        val active = resources ?: return
        resources = null
        browser = null
        active.manager.destroyAll()
    }

    private fun openBrowser(active: Resources, url: String, attach: (BrowserHandle) -> Unit) {
        check(browser == null) { "A browser already exists. Call destroyBrowser() before creating another one." }
        val created = active.manager.create(url)
        browser = created
        try {
            attach(created.handle)
        } catch (failure: Throwable) {
            browser = null
            active.manager.destroy(created.handle)
            throw failure
        }
    }

    private fun resourcesForCreation(): Resources {
        requireMainThread("createBrowser()")
        return checkNotNull(resources) { "This BrowserHost has been shut down." }
    }

    private fun requireMainThread(operation: String) {
        check(isMainThread()) { "$operation must be called on the main thread." }
    }

    companion object {
        fun forContext(context: Context): AndroidBrowserHost {
            val manager = AndroidBrowserManager(context)
            return AndroidBrowserHost(
                manager = manager,
                attach = { handle, container -> manager.attachTo(handle, container) },
                isMainThread = { Looper.getMainLooper().thread === Thread.currentThread() },
            )
        }
    }
}
