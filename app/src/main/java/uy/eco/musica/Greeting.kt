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
            lastVisit <= 0 -> pick(
                Greeting("¡$n ESTÁ EN LA CASA!", "Bienvenido a la banda. Dale play 🎶"),
                Greeting("¡LLEGÓ $n Y SE ARMÓ! 🎉", "Ya sos de la casa, no hay vuelta atrás"),
                Greeting("$n, BIENVENIDO AL VIP 😎", "Acá entra solo gente con buen gusto"),
                Greeting("¡ATENCIÓN, ENTRÓ $n!", "Bajen la cumbia que llegó el jefe… no, mentira, súbanla"),
                Greeting("$n, ESTO QUEDA ENTRE NOS 🤫", "Música gratis y sin anuncios. No le cuentes a cualquiera"),
                Greeting("¡AL FIN, $n!", "Te estábamos esperando para arrancar la joda"),
            )
            days >= 7 -> pick(
                Greeting("$n, ¿DÓNDE ANDABAS?", "Te guardamos el lugar en la pista"),
                Greeting("¡APARECIÓ $n! 🙌", "Pensábamos que te habías pasado a Spotify 😅"),
                Greeting("¡$n, TE HICISTE DESEAR! 😏", "Tanto misterio para volver igual"),
                Greeting("$n, ¿ANDABAS DE NOVIO?", "Te perdiste unos temazos"),
                Greeting("¡VOLVIÓ EL HIJO PRÓDIGO!", "$n, ponete al día que hay música nueva"),
                Greeting("$n, NO NOS HAGAS ESTO 😢", "Tres días sin vos es mucho"),
                Greeting("¡QUÉ ALEGRÍA, $n!", "Ya te estábamos por mandar un audio"),
            )
            days >= 3 -> pick(
                Greeting("¡VOLVISTE, $n!", "La música te extrañaba"),
                Greeting("$n, ¿TE SECUESTRARON? 👀", "Parpadeá dos veces si estás bien"),
                Greeting("¡MIRÁ QUIÉN SE ACORDÓ!", "$n, ya íbamos a hacer la denuncia"),
                Greeting("$n, ¿NOS ESTÁS GORREANDO? 😏", "Decinos quién es esa otra app"),
                Greeting("¡RESUCITÓ $n! 🙏", "Prendan una vela, que volvió"),
                Greeting("$n, ¿TE MUDASTE?", "Tu lugar en la pista sigue libre, eh"),
            )
            month == Calendar.DECEMBER && coin -> pick(
                Greeting("$n, ¿YA SE DECRETÓ LA BAJADA? 🕺", "Mientras tanto, calentamos acá"),
                Greeting("$n, DICIEMBRE NO PERDONA 🍾", "Hígado listo, playlist también"),
                Greeting("¡SE VIENEN LAS FIESTAS, $n! 🎄", "¿Quién pone la música? Obvio que vos"),
                Greeting("$n, MODO DICIEMBRE ACTIVADO", "Una despedida por día, mínimo"),
                Greeting("¡$n, ESTE AÑO SE BAILA! 🕺", "Que no te agarre el brindis sin playlist"),
                Greeting("$n, ¿YA ARMASTE LA DE AÑO NUEVO?", "Que la abuela también baile"),
            )
            ((dow == Calendar.FRIDAY && hour >= 20) || dow == Calendar.SATURDAY) && coin -> pick(
                Greeting("¡$n, ES FINDE! 🔥", "El cuerpo lo sabe"),
                Greeting("¡$n, HOY NO SE DUERME! 🔥", "Avisá en casa que llegás tarde"),
                Greeting("¿PREVIA, $n? 🍻", "La música ya está, vos traé el hielo"),
                Greeting("$n, EL FINDE TE LLAMA 📞", "¿Lo vas a dejar sonando?"),
                Greeting("¡SE PICÓ, $n!", "Hoy se sale, después vemos"),
                Greeting("$n, HOY TE QUEREMOS VER BAILAR 💃", "Y no vale hacerse el tímido"),
            )
            dow == Calendar.SUNDAY && coin -> pick(
                Greeting("DOMINGO, $n", "Ritmo tranqui y mate en mano"),
                Greeting("DOMINGO, $n 😴", "¿Resaca o santidad? No juzgamos"),
                Greeting("$n, ¿ASADO Y MÚSICA? 🥩", "El fuego lo prendés vos"),
                Greeting("¡DOMINGUEANDO, $n!", "Sillón, mate y temazos"),
                Greeting("$n, ¿CÓMO TERMINÓ ANOCHE? 👀", "Contá, que acá no sale nada"),
                Greeting("DOMINGO DE RECUPERACIÓN, $n", "Volumen bajito, que todavía duele"),
            )
            sameDay -> pick(
                Greeting("OTRA VEZ POR ACÁ, $n 😎", "¿No podés parar, eh?"),
                Greeting("¡$n, OTRA VEZ! 😂", "Admitilo: no podés vivir sin nosotros"),
                Greeting("¿VOLVISTE, $n?", "Sabíamos que no aguantabas"),
                Greeting("$n, ESTO YA ES AMOR ❤️", "Y es correspondido"),
                Greeting("¡TE ENGANCHASTE, $n!", "Tranqui, es la mejor adicción"),
                Greeting("$n, ¿TE OLVIDASTE ALGO?", "Ah no, era el temazo de recién"),
            )
            hour < 6 -> pick(
                Greeting("HOLA $n 🌙", "¿Insomnio o after? Acá hay música para los dos"),
                Greeting("$n, ¿TODAVÍA DESPIERTO?", "La noche es larga, que el ritmo no pare"),
                Greeting("$n, ¿QUÉ HACÉS DESPIERTO? 🦉", "No preguntamos con quién"),
                Greeting("¡HORA PELIGROSA, $n! 🌙", "Lo que pasa a esta hora queda entre nosotros"),
                Greeting("$n, ¿VOLVÉS O RECIÉN SALÍS?", "Igual te banco"),
                Greeting("¿NO PODÉS DORMIR, $n?", "Un lentito y a la cama"),
                Greeting("$n, LA NOCHE ES JOVEN 😏", "Vos no tanto, pero bueno"),
            )
            hour < 12 -> pick(
                Greeting("BUEN DÍA, $n ☀️", "¿Mate y música? Arrancamos"),
                Greeting("ARRIBA, $n", "Que el día no se escucha solo"),
                Greeting("¡BUEN DÍA, $n! ☕", "Primero la música, después la gente"),
                Greeting("$n, ARRIBA QUE SE PUEDE 💪", "Un temazo y arrancás como nuevo"),
                Greeting("¿YA DESAYUNASTE, $n?", "Acá hay un temazo para mojar en el café"),
                Greeting("BUEN DÍA, $n ☀️", "Cara de dormido, pero con estilo"),
                Greeting("$n, HOY VA A SER UN GRAN DÍA", "O por lo menos va a tener buena música"),
            )
            hour < 15 -> pick(
                Greeting("HOLA $n 🍝", "¿Qué suena con el almuerzo?"),
                Greeting("¡$n, A COMER! 🍕", "Que la música vaya de postre"),
                Greeting("$n, ¿SIESTA O CUMBIA?", "Se puede hacer las dos, eh"),
                Greeting("HOLA $n 🍗", "¿Qué te tocó hoy, tupper o delivery?"),
                Greeting("$n, MITAD DEL DÍA", "Ya hiciste bastante, date un gusto"),
                Greeting("¡BUEN PROVECHO, $n!", "Mastique despacio y con ritmo"),
            )
            hour < 20 -> pick(
                Greeting("BUENAS TARDES, $n", "Hora del repaso musical"),
                Greeting("HOLA $n", "La tarde pide un temazo"),
                Greeting("$n, ¿FALTA MUCHO PARA SALIR? ⏰", "Aguantá que te acompañamos"),
                Greeting("¡LA TARDE ES NUESTRA, $n!", "Mate, bizcochos y temazos"),
                Greeting("$n, HORA DE ESCAPARSE UN RATO 🏃", "Nadie se va a enterar"),
                Greeting("¡QUÉ HACÉS, $n!", "Dale que la tarde se pasa volando"),
                Greeting("$n, ¿YA PENSASTE EN LA NOCHE? 😏", "Nosotros sí"),
            )
            else -> pick(
                Greeting("BUENAS NOCHES, $n 🌃", "¿Armamos la previa?"),
                Greeting("HOLA $n", "Bajamos las luces, subimos el volumen"),
                Greeting("$n, ¿SALIDA O SILLÓN? 🛋️", "Tenemos música para las dos"),
                Greeting("¡BUENAS NOCHES, $n! 🌃", "Que no te gane el sueño tan temprano"),
                Greeting("$n, ESTA NOCHE PROMETE 🔥", "Vos poné la música, el resto se acomoda"),
                Greeting("¿CENA CON MÚSICA, $n? 🍷", "Romántico o bailable, vos elegís"),
                Greeting("$n, NOCHE DE TEMAZOS", "El vecino que se aguante"),
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
