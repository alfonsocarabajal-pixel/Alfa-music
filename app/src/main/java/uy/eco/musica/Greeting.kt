package uy.eco.musica

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar

/** Saludo de bienvenida: título grande + frase. */
data class Greeting(val title: String, val line: String)

object Greetings {

    private fun pick(vararg options: Greeting) = options.random()

    /**
     * Elige el saludo. Prioridad: primera vez → tiempo sin entrar → diciembre / fin de semana /
     * domingo (a veces, para que no se repita siempre) → ya entró hoy → hora del día.
     */
    fun choose(rawName: String, lastVisit: Long, now: Calendar = Calendar.getInstance()): Greeting {
        val n = rawName.trim().uppercase().ifBlank { "CRACK" }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val dow = now.get(Calendar.DAY_OF_WEEK)
        val month = now.get(Calendar.MONTH)
        val days = if (lastVisit > 0) ((now.timeInMillis - lastVisit) / 86_400_000L).toInt() else -1
        val sameDay = lastVisit > 0 && Calendar.getInstance().apply { timeInMillis = lastVisit }.let {
            it.get(Calendar.YEAR) == now.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        }
        val coin = Math.random() < 0.5

        return when {
            lastVisit <= 0 -> Greeting("¡$n ESTÁ EN LA CASA!", "Bienvenido a la banda. Dale play 🎶")
            days >= 7 -> pick(
                Greeting("$n, ¿DÓNDE ANDABAS?", "Te guardamos el lugar en la pista"),
                Greeting("¡APARECIÓ $n! 🙌", "Pensábamos que te habías pasado a Spotify 😅"),
            )
            days >= 3 -> Greeting("¡VOLVISTE, $n!", "La música te extrañaba")
            month == Calendar.DECEMBER && coin ->
                Greeting("$n, ¿YA SE DECRETÓ LA BAJADA? 🕺", "Mientras tanto, calentamos acá")
            ((dow == Calendar.FRIDAY && hour >= 20) || dow == Calendar.SATURDAY) && coin ->
                Greeting("¡$n, ES FINDE! 🔥", "El cuerpo lo sabe")
            dow == Calendar.SUNDAY && coin -> Greeting("DOMINGO, $n", "Ritmo tranqui y mate en mano")
            sameDay -> Greeting("OTRA VEZ POR ACÁ, $n 😎", "¿No podés parar, eh?")
            hour < 6 -> pick(
                Greeting("HOLA $n 🌙", "¿Insomnio o after? Acá hay música para los dos"),
                Greeting("$n, ¿TODAVÍA DESPIERTO?", "La noche es larga, que el ritmo no pare"),
            )
            hour < 12 -> pick(
                Greeting("BUEN DÍA, $n ☀️", "¿Mate y música? Arrancamos"),
                Greeting("ARRIBA, $n", "Que el día no se escucha solo"),
            )
            hour < 15 -> Greeting("HOLA $n 🍝", "¿Qué suena con el almuerzo?")
            hour < 20 -> pick(
                Greeting("BUENAS TARDES, $n", "Hora del repaso musical"),
                Greeting("HOLA $n", "La tarde pide un temazo"),
            )
            else -> pick(
                Greeting("BUENAS NOCHES, $n 🌃", "¿Armamos la previa?"),
                Greeting("HOLA $n", "Bajamos las luces, subimos el volumen"),
            )
        }
    }
}

/** Muestra el saludo una vez al abrir la app; se va solo a los 7 segundos o al tocarlo. */
@Composable
fun GreetingBanner(name: String) {
    if (name.isBlank()) return
    val context = LocalContext.current
    var shown by rememberSaveable { mutableStateOf(false) } // una vez por apertura
    var visible by remember { mutableStateOf(false) }
    var greeting by remember { mutableStateOf<Greeting?>(null) }

    LaunchedEffect(Unit) {
        if (shown) return@LaunchedEffect
        shown = true
        val sp = context.getSharedPreferences("saludo", Context.MODE_PRIVATE)
        greeting = Greetings.choose(name, sp.getLong("ultima_visita", 0L))
        sp.edit().putLong("ultima_visita", System.currentTimeMillis()).apply()
        visible = true
        delay(7_000)
        visible = false
    }

    val g = greeting ?: return
    AnimatedVisibility(visible = visible, exit = fadeOut() + shrinkVertically()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { visible = false },
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    g.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    g.line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
