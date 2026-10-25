package io.github.shoalapps.swordfish

import io.github.shoalapps.swordfish.data.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTest {
    @Test fun readsLrc() {
        val lines = Lyrics.parseLrc("[00:06.89]Coming out of my cage\n[01:08.25]And I've been doing just fine")
        assertEquals(6890L, lines[0].atMs)
        assertEquals(68250L, lines[1].atMs)
        assertEquals("And I've been doing just fine", lines[1].text)
    }

    @Test fun readsWordTimedTtml() {
        val ttml = """<tt><body><div><p begin="6.986" end="8.463"><span begin="6.986" end="7.499">Coming</span> <span begin="7.499" end="7.712">out</span></p><p begin="1:02.5" end="1:03"><span begin="1:02.5" end="1:03">Jealousy</span></p></div></body></tt>"""
        val lines = Lyrics.parseTtml(ttml)
        assertEquals(2, lines.size)
        assertEquals("Coming out", lines[0].text)
        assertEquals(6986L, lines[0].atMs)
        assertEquals(7499L, lines[0].words[1].startMs)
        assertEquals(62500L, lines[1].atMs)
    }

    @Test fun cleansYouTubeTitles() {
        assertEquals("Mr. Brightside", Lyrics.clean("Mr. Brightside (Official Music Video)"))
    }
}
