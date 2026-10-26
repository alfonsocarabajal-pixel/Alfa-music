package uy.eco.musica

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GreetingsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int) =
        Calendar.getInstance().apply { set(year, month, day, hour, 0, 0) }

    @Test
    fun primeraVez() {
        val g = Greetings.choose("Choco", 0, at(2026, Calendar.OCTOBER, 26, 10))
        assertTrue(g.title, g.title == "¡CHOCO ESTÁ EN LA CASA!")
    }

    @Test
    fun masDeUnaSemanaUsaLosDosMensajes() {
        val now = at(2026, Calendar.OCTOBER, 26, 10)
        val last = now.timeInMillis - 10L * 86_400_000L
        val titles = (1..60).map { Greetings.choose("Choco", last, now).title }.toSet()
        assertTrue(titles.toString(), titles == setOf("CHOCO, ¿DÓNDE ANDABAS?", "¡APARECIÓ CHOCO! 🙌"))
    }

    @Test
    fun mananaDeMartes() {
        val now = at(2026, Calendar.OCTOBER, 27, 9) // martes
        val last = now.timeInMillis - 86_400_000L
        val g = Greetings.choose("choco", last, now)
        assertTrue(g.title, g.title == "BUEN DÍA, CHOCO ☀️" || g.title == "ARRIBA, CHOCO")
    }

    @Test
    fun yaEntroHoyUnMartes() {
        val now = at(2026, Calendar.OCTOBER, 27, 18)
        val last = now.timeInMillis - 2L * 3_600_000L
        val g = Greetings.choose("Choco", last, now)
        assertTrue(g.title, g.title == "OTRA VEZ POR ACÁ, CHOCO 😎")
    }
}
