package io.github.nideta231.hermesremote

import io.github.nideta231.hermesremote.data.ChatItem
import io.github.nideta231.hermesremote.data.ToolStatus
import io.github.nideta231.hermesremote.data.reuseKeys
import io.github.nideta231.hermesremote.ui.nextRevealLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothStreamingTest {
    @Test fun revealAdvancesSteadilyAndNeverOvershoots() {
        // At the ~120 chars/s floor: at least one char per frame, two over 20 ms.
        assertEquals(1, nextRevealLength(0, 5, 16))
        assertEquals(2, nextRevealLength(0, 5, 20))
        assertEquals(5, nextRevealLength(5, 5, 16))
        assertEquals(5, nextRevealLength(9, 5, 16)) // text got shorter (rebuild): clamp
        assertEquals(10, nextRevealLength(10, 10, 1000))
    }

    @Test fun aBigBurstDrainsWithinAboutAThirdOfASecond() {
        var shown = 0
        var frames = 0
        while (shown < 600 && frames < 100) { shown = nextRevealLength(shown, 600, 16); frames++ }
        assertEquals(600, shown)
        assertTrue("took $frames frames", frames <= 45) // ~0.7 s even for 600 chars at once
    }

    @Test fun settledHistoryKeepsLiveKeysSoNothingReanimates() {
        val live = listOf(
            ChatItem.User("u-1", "hello"),
            ChatItem.Tool("live-5", "terminal", "ls", status = ToolStatus.RUNNING, callId = "c1"),
            ChatItem.Assistant("live-9", "Here is the listing", streaming = true),
        )
        val fresh = listOf(
            ChatItem.User("h-r1", "hello"),
            ChatItem.Tool("h-r2", "terminal", "ls", callId = "c1"),
            ChatItem.Assistant("h-r3", "Here is the listing, done."),
        )
        val merged = reuseKeys(live, fresh)
        assertEquals(listOf("u-1", "live-5", "live-9"), merged.map { it.key })
        assertEquals("Here is the listing, done.", (merged[2] as ChatItem.Assistant).text)
    }
}
