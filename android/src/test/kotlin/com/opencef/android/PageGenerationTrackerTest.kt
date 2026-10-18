package com.opencef.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGenerationTrackerTest {
    @Test fun staleTokenIsRejected() {
        val tracker = PageGenerationTracker()
        tracker.advance()
        val old = tracker.currentToken()
        tracker.advance()
        assertFalse(tracker.isCurrent(old))
    }

    @Test fun currentTokenIsAccepted() {
        val tracker = PageGenerationTracker()
        tracker.advance()
        assertTrue(tracker.isCurrent(tracker.currentToken()))
    }

    @Test fun firstTokenIsAccepted() {
        val tracker = PageGenerationTracker()
        tracker.advance()
        assertTrue(tracker.isCurrent(tracker.currentToken()))
    }

    @Test fun onlyLatestTokenIsAccepted() {
        val tracker = PageGenerationTracker()
        tracker.advance()
        val first = tracker.currentToken()
        tracker.advance()
        val second = tracker.currentToken()
        tracker.advance()
        val third = tracker.currentToken()
        assertFalse(tracker.isCurrent(first))
        assertFalse(tracker.isCurrent(second))
        assertTrue(tracker.isCurrent(third))
    }

    @Test fun advanceWithoutCapturingTokenIsSafe() {
        val tracker = PageGenerationTracker()
        tracker.advance()
        tracker.advance()
        assertTrue(tracker.isCurrent(tracker.currentToken()))
    }
}