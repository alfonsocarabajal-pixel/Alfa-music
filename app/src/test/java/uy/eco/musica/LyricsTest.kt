package uy.eco.musica

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {

    @Test
    fun limpiaTitulosDeVideosKaraoke() {
        assertEquals("Queen - Bohemian Rhapsody", Lyrics.cleanTitle("Queen - Bohemian Rhapsody (Karaoke Version)"))
        assertEquals("Bohemian Rhapsody - Queen", Lyrics.cleanTitle("Bohemian Rhapsody - Queen | Karaoke Version | KaraFun"))
        assertEquals("Shakira - Hips Don't Lie", Lyrics.cleanTitle("Shakira - Hips Don't Lie [Karaoke Instrumental]"))
        assertEquals("Soda Stereo - De Música Ligera", Lyrics.cleanTitle("Soda Stereo - De Música Ligera KARAOKE"))
    }

    @Test
    fun leeElFormatoLrc() {
        val lines = Lyrics.parse("[00:12.50] Primera\n[00:15.00]Segunda\n[01:02.3] Tercera")
        assertEquals(3, lines.size)
        assertEquals(12_500L, lines[0].timeMs)
        assertEquals("Segunda", lines[1].text)
        assertEquals(62_300L, lines[2].timeMs)
    }

    /** Prueba real contra LRCLIB, con un título como los de los videos karaoke. */
    @Test
    fun encuentraLaLetraDeUnVideoKaraoke() {
        val lines = Lyrics.find(
            title = "Queen - Bohemian Rhapsody (Karaoke Version)",
            artist = "Sing King",
            typedQuery = "bohemian rhapsody",
            durationSec = 355,
        )
        assertTrue("No se encontró la letra sincronizada", !lines.isNullOrEmpty())
        assertFalse(lines!!.all { it.text.isBlank() })
    }
}
