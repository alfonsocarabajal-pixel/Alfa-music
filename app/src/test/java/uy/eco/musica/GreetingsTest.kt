package uy.eco.musica

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GreetingsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int) =
        Calendar.getInstance().apply { set(year, month, day, hour, 0, 0) }

    @Test
    fun primeraVez() {
        val titles = (1..200).map { Greetings.choose("Choco", 0, at(2026, Calendar.OCTOBER, 26, 10)).title }.toSet()
        assertTrue(titles.toString(), "¡CHOCO ESTÁ EN LA CASA!" in titles && "¡AL FIN, CHOCO!" in titles && titles.size == 6)
    }

    @Test
    fun masDeUnaSemanaUsaLosSieteMensajes() {
        val now = at(2026, Calendar.OCTOBER, 26, 10)
        val last = now.timeInMillis - 10L * 86_400_000L
        val titles = (1..300).map { Greetings.choose("Choco", last, now).title }.toSet()
        assertTrue(titles.toString(), titles.size == 7 && "CHOCO, ¿DÓNDE ANDABAS?" in titles &&
            "¡QUÉ ALEGRÍA, CHOCO!" in titles && "¡RESUCITÓ CHOCO! 🙏" !in titles)
    }

    @Test
    fun deTresASeisDias() {
        val now = at(2026, Calendar.OCTOBER, 26, 10)
        val last = now.timeInMillis - 4L * 86_400_000L
        val titles = (1..300).map { Greetings.choose("Choco", last, now).title }.toSet()
        assertTrue(titles.toString(), titles.size == 6 && "¡VOLVISTE, CHOCO!" in titles &&
            "¡RESUCITÓ CHOCO! 🙏" in titles && "¡QUÉ ALEGRÍA, CHOCO!" !in titles)
    }

    @Test
    fun mananaDeMartes() {
        val now = at(2026, Calendar.OCTOBER, 27, 9) // martes
        val last = now.timeInMillis - 86_400_000L
        val g = Greetings.choose("choco", last, now)
        assertTrue(g.title, g.title.contains("CHOCO") && g.line.isNotBlank())
        val titles = (1..300).map { Greetings.choose("choco", last, now).title }.toSet()
        assertTrue(titles.toString(), "ARRIBA, CHOCO" in titles && "¿YA DESAYUNASTE, CHOCO?" in titles)
    }

    @Test
    fun yaEntroHoyUnMartes() {
        val now = at(2026, Calendar.OCTOBER, 27, 18)
        val last = now.timeInMillis - 2L * 3_600_000L
        val g = Greetings.choose("Choco", last, now)
        val esperados = setOf("OTRA VEZ POR ACÁ, CHOCO 😎", "¡CHOCO, OTRA VEZ! 😂", "¿VOLVISTE, CHOCO?",
            "CHOCO, ESTO YA ES AMOR ❤️", "¡TE ENGANCHASTE, CHOCO!", "CHOCO, ¿TE OLVIDASTE ALGO?")
        assertTrue(g.title, g.title in esperados)
    }
}
