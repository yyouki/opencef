package com.opencef.host

import android.content.Context
import android.view.ViewGroup

interface BrowserHost {

    val hasBrowser: Boolean

    val isShutdown: Boolean

    fun createBrowser(url: String, container: ViewGroup)

    fun loadUrl(url: String)

    fun reload()

    fun show()

    fun hide()

    fun on(event: String, listener: (payload: String) -> Unit)

    fun off(event: String, listener: (payload: String) -> Unit)

    fun emit(event: String, payload: String)

    fun destroyBrowser()

    fun shutdown()

    companion object {
        fun create(context: Context): BrowserHost = AndroidBrowserHost.forContext(context)
    }
}
