package com.opencef.android

// Tracks page navigation "generations" so a readiness completion callback
// captured for an earlier navigation can be recognized as stale once a
// newer navigation has started. Deliberately has zero Android dependency:
// the guarantee this makes (only the current generation's token can ever
// match) is a plain integer-comparison rule, exercised directly and
// deterministically by PageGenerationTrackerTest - not approximated by it.
// AndroidBrowserManager's WebViewClient is the only production caller and
// uses this exact class; there is no separate test-only implementation.
//
// Call pattern:
//   onPageStarted()  -> advance()
//   onPageFinished() -> val token = currentToken()   (captured before the
//                        async evaluateJavascript() call starts)
//   the async completion callback -> isCurrent(token) before acting
//
// onPageStarted()/onPageFinished()/the evaluateJavascript() completion
// callback are all documented to run on the WebView's UI thread only, so
// this only needs to guard against a stale read, never a concurrent write -
// a plain Int needs no synchronization here.
internal class PageGenerationTracker {

    // No overflow wraparound: reaching Int.MAX_VALUE would need billions of
    // navigations within a single WebView's lifetime, which is not a
    // realistic scenario for a UI framework - handling it would be
    // complexity with no practical benefit.
    private var generation = 0

    fun advance() {
        generation++
    }

    fun currentToken(): Int = generation

    fun isCurrent(token: Int): Boolean = token == generation
}
