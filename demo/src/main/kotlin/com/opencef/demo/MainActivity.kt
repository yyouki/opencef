package com.opencef.demo

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.opencef.host.BrowserHost
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    @Volatile
    private var host: BrowserHost? = null

    private val hostEventCount = AtomicInteger(0)

    private lateinit var statusView: TextView

    private var pageState = "starting"
    private var visibilityState = "visible"
    private var runtimeReady = false
    private var lastHostEvent = "none yet"
    private var lastRoundTrip = "none yet"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply {
            textSize = 14f
            setPadding(24, 16, 24, 16)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val browserContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(statusView)
            addView(controls)
            addView(browserContainer)
        }
        setContentView(root)

        val browserHost = BrowserHost.create(this)
        host = browserHost
        browserHost.createBrowser(DEMO_URL, browserContainer)
        registerBrowserListeners(browserHost)

        addControlButton(controls, "Show") { host?.show() }
        addControlButton(controls, "Hide") { host?.hide() }
        addControlButton(controls, "Reload") { host?.reload() }
        addControlButton(controls, "Destroy") { host?.destroyBrowser() }

        renderStatus()
    }

    override fun onDestroy() {
        host?.shutdown()
        host = null
        super.onDestroy()
    }

    private fun registerBrowserListeners(browserHost: BrowserHost) {
        browserHost.on("loading") {
            updateStatus {
                pageState = "loading"
                runtimeReady = false
            }
        }
        browserHost.on("loaded") { updateStatus { pageState = "loaded" } }
        browserHost.on("error") { updateStatus { pageState = "error" } }
        browserHost.on("visibilityChanged") { payload ->
            val visible = readVisible(payload)
            updateStatus { visibilityState = if (visible) "visible" else "hidden" }
        }
        browserHost.on("destroyed") {
            updateStatus {
                pageState = "destroyed"
                runtimeReady = false
            }
        }
        browserHost.on("host_page_ready") { updateStatus { runtimeReady = true } }
        browserHost.on("host_test_event") { payload -> handleTestEvent(payload) }
    }

    private fun handleTestEvent(payload: String) {
        val request = runCatching { JSONObject(payload) }.getOrNull()
        val sequence = request?.optInt("sequence", 0) ?: 0
        val text = request?.optString("text", "").orEmpty()
        val hostEventNumber = hostEventCount.incrementAndGet()

        val response = JSONObject()
            .put("sequence", sequence)
            .put("text", "Host received: $text")
            .put("hostEventCount", hostEventNumber)
            .toString()
        host?.emit("host_test_result", response)

        updateStatus {
            lastHostEvent = "host_test_event #$hostEventNumber (page sequence $sequence)"
            lastRoundTrip = "host_test_result #$hostEventNumber sent to page"
        }
    }

    private fun readVisible(payload: String): Boolean {
        return runCatching { JSONObject(payload).optBoolean("visible", true) }.getOrDefault(true)
    }

    private fun updateStatus(change: () -> Unit) {
        runOnUiThread {
            change()
            renderStatus()
        }
    }

    private fun renderStatus() {
        statusView.text = buildString {
            if (pageState == "destroyed") {
                appendLine("Browser: destroyed")
            } else {
                appendLine("Browser: $pageState, $visibilityState")
            }
            appendLine("Runtime ready (page-reported): ${if (runtimeReady) "yes" else "no"}")
            appendLine("Host event received: $lastHostEvent")
            append("Round trip: $lastRoundTrip")
        }
    }

    private fun addControlButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { action() }
        }
        parent.addView(button)
    }

    private companion object {
        const val DEMO_URL = "file:///android_asset/www/host_demo.html"
    }
}
