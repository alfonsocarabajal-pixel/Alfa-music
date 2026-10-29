package uy.eco.musica

import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmisorasTest {
    private val arr = JSONArray(File("src/main/assets/emisoras.json").readText())
    private val list = (0 until arr.length()).map { arr.getJSONObject(it) }

    @Test
    fun hayEmisorasAmYFm() {
        assertTrue(list.any { it.getString("banda") == "AM" })
        assertTrue(list.any { it.getString("banda") == "FM" })
        assertTrue(list.all { it.getString("banda") in setOf("AM", "FM") })
    }

    @Test
    fun cadaEmisoraTieneNombreYDireccion() {
        val ids = list.map { it.getString("id") }
        assertTrue("ids repetidos", ids.size == ids.toSet().size)
        list.forEach {
            assertTrue(it.toString(), it.getString("nombre").isNotBlank())
            assertTrue(it.toString(), it.getString("url").matches(Regex("^https?://.+")))
        }
    }
}
