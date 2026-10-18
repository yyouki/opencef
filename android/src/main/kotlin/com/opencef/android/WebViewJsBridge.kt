package com.opencef.android

import android.webkit.JavascriptInterface
import com.opencef.core.JSBridge
import java.util.concurrent.atomic.AtomicBoolean

class WebViewJsBridge(private val jsBridge: JSBridge) {

    // Backs window.cef.isReady(): the one signal that is safe to call from
    // page JavaScript at any time, including before any injected script has
    // run, since it is a native-backed method available from the moment
    // addJavascriptInterface() takes effect - not a JS property or function
    // that itself depends on injection timing.
    private val ready = AtomicBoolean(false)

    // Android does not guarantee @JavascriptInterface methods run on the UI
    // thread - in practice this runs on a WebView-internal thread. onMessage()
    // (EventBus) then invokes listeners synchronously on that same thread.
    @JavascriptInterface
    fun emit(event: String, payload: String) {
        jsBridge.onMessage(event, payload)
    }

    @JavascriptInterface
    fun isReady(): Boolean = ready.get()

    // Not JS-exposed: called from Kotlin (AndroidBrowserManager's
    // WebViewClient) to flip readiness as each page starts/finishes loading.
    fun setReady(value: Boolean) {
        ready.set(value)
    }
}
