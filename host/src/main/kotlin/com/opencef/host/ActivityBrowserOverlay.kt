package com.opencef.host

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Adds a small, toggleable WebView overlay to an existing Activity.
 *
 * Intended for native/game Activities that already render beneath Android
 * Views. The closed overlay is GONE, so it does not intercept game input.
 * When open, the browser intentionally covers the game surface.
 *
 * SECURITY: only bundled assets under file:///android_asset/ are accepted.
 * The OpenCEF JavaScript bridge is available to the loaded page; do not load
 * arbitrary or untrusted remote content through this helper.
 *
 * Create, show, hide, and dispose this object on the Android main thread.
 * Always call dispose() from the owning Activity's onDestroy().
 */
class ActivityBrowserOverlay @JvmOverloads constructor(
    ownerActivity: Activity,
    initialUrl: String,
    buttonLabel: String = "WEB",
) {

    // Check the thread before constructing any Android View instances.
    private val activity: Activity = requireMainThread(ownerActivity)
    private val host: BrowserHost = BrowserHost.create(activity)
    private val browserLayer = FrameLayout(activity)
    private val browserContainer = FrameLayout(activity)
    private val openButton = createLabel(buttonLabel, "Open browser overlay")
    private val closeButton = createLabel("×", "Close browser overlay")

    @Volatile
    private var disposed = false

    val isVisible: Boolean
        get() = !disposed && browserLayer.visibility == View.VISIBLE

    val isDisposed: Boolean
        get() = disposed

    init {
        requireBundledAssetUrl(initialUrl)

        browserLayer.apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }
        browserLayer.addView(
            browserContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        closeButton.apply {
            textSize = 24f
            visibility = View.VISIBLE
            setOnClickListener { hide() }
        }
        browserLayer.addView(
            closeButton,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(12)
                marginEnd = dp(12)
            },
        )

        openButton.setOnClickListener { show() }

        // Add the full-screen layer first and the small open button afterwards.
        // A GONE full-screen layer does not cover the game or intercept taps.
        activity.addContentView(
            browserLayer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.addContentView(
            openButton,
            FrameLayout.LayoutParams(dp(64), dp(42), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(12)
                marginEnd = dp(12)
            },
        )

        host.createBrowser(initialUrl, browserContainer)
        host.hide()
    }

    fun show() {
        checkMainThread()
        checkActive()
        host.show()
        browserLayer.visibility = View.VISIBLE
        openButton.visibility = View.GONE
        browserLayer.bringToFront()
    }

    fun hide() {
        checkMainThread()
        if (disposed) return
        host.hide()
        browserLayer.visibility = View.GONE
        openButton.visibility = View.VISIBLE
        openButton.bringToFront()
    }

    fun toggle() {
        checkMainThread()
        checkActive()
        if (isVisible) hide() else show()
    }

    /**
     * Navigate only to a bundled local asset. This restriction is deliberate:
     * the bridge is not origin-scoped and must not be exposed to arbitrary web
     * pages. Add an explicit origin allowlist before supporting remote pages.
     */
    fun loadBundledAsset(url: String) {
        checkMainThread()
        checkActive()
        requireBundledAssetUrl(url)
        host.loadUrl(url)
    }

    fun reload() {
        checkMainThread()
        checkActive()
        host.reload()
    }

    fun on(event: String, listener: (payload: String) -> Unit) {
        checkActive()
        host.on(event, listener)
    }

    fun off(event: String, listener: (payload: String) -> Unit) {
        if (disposed) return
        host.off(event, listener)
    }

    fun emit(event: String, payload: String) {
        if (disposed) return
        host.emit(event, payload)
    }

    /** Call once from the owning Activity's onDestroy(). */
    fun dispose() {
        checkMainThread()
        if (disposed) return
        disposed = true
        try {
            host.shutdown()
        } finally {
            (browserLayer.parent as? ViewGroup)?.removeView(browserLayer)
            (openButton.parent as? ViewGroup)?.removeView(openButton)
        }
    }

    private fun createLabel(label: String, description: String): TextView =
        TextView(activity).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.rgb(35, 48, 62))
            }
        }

    private fun requireBundledAssetUrl(url: String) {
        val path = url.substringBefore('?').substringBefore('#')
        require(path.startsWith(ASSET_PREFIX)) {
            "ActivityBrowserOverlay accepts only bundled assets: $ASSET_PREFIX..."
        }
        val relativePath = Uri.decode(path.removePrefix(ASSET_PREFIX))
        require(relativePath.isNotBlank() && relativePath.split('/').none { it == "." || it == ".." }) {
            "Invalid bundled asset path."
        }
    }

    private fun checkActive() {
        check(!disposed) { "ActivityBrowserOverlay has been disposed." }
    }

    private fun checkMainThread() {
        requireMainThread(activity)
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val ASSET_PREFIX = "file:///android_asset/"

        fun requireMainThread(activity: Activity): Activity {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "ActivityBrowserOverlay UI operations must run on the main thread."
            }
            return activity
        }
    }
}
