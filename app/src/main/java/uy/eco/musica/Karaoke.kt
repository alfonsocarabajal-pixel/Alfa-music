package uy.eco.musica

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pestaña Karaoke: busca versiones karaoke/instrumentales y muestra la letra sincronizada.
 * Cuando suena una canción de karaoke, muestra la letra; "Buscar otra" vuelve a la búsqueda.
 */
@Composable
fun KaraokeScreen(ui: PlayerUi) {
    val current = ui.current
    val playingKaraoke = current?.karaokeQuery != null
    var searching by rememberSaveable { mutableStateOf(false) }

    // Al empezar una canción de karaoke se pasa a la letra automáticamente.
    LaunchedEffect(current?.mediaId) { if (playingKaraoke) searching = false }

    if (playingKaraoke && !searching && current != null) {
        LyricsView(ui, onSearch = { searching = true })
    } else {
        KaraokeSearch(ui, onBackToLyrics = if (playingKaraoke) ({ searching = false }) else null)
    }
}

@Composable
private fun KaraokeSearch(ui: PlayerUi, onBackToLyrics: (() -> Unit)?) {
    var query by rememberSaveable { mutableStateOf("") }
    var lastQuery by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>("Buscá una canción para cantar: te muestro sus versiones karaoke.") }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        lastQuery = q
        loading = true
        message = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Repo.searchKaraoke(q) } }
            loading = false
            r.onSuccess {
                results = it
                if (it.isEmpty()) message = "No encontré versiones karaoke de esa canción."
            }.onFailure {
                ErrorLog.report("Buscar karaoke", "«$q» · ${ErrorLog.describe(it)}")
                results = emptyList()
                message = "No se pudo buscar. Revisá la conexión o probá de nuevo."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackToLyrics != null) {
                IconButton(onClick = onBackToLyrics) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a la letra")
                }
            } else {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Canción para cantar") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                trailingIcon = {
                    IconButton(onClick = { search() }) { Icon(Icons.Default.Search, contentDescription = "Buscar") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                results.isEmpty() -> CenterMessage(message.orEmpty())
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { song ->
                        SongRow(
                            thumb = song.thumb,
                            title = song.title,
                            subtitle = listOf(song.artist, formatTime(song.durationSec))
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            highlighted = ui.current?.mediaId == song.id,
                            onClick = { ui.playKaraoke(song, lastQuery) },
                        ) {
                            IconButton(onClick = { ui.enqueueKaraoke(song, lastQuery) }) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar a la cola")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Letra grande, con la línea actual resaltada y desplazamiento automático. */
@Composable
private fun LyricsView(ui: PlayerUi, onSearch: () -> Unit) {
    val item = ui.current ?: return
    val song = remember(item.mediaId) { item.toSong() }
    var lines by remember(item.mediaId) { mutableStateOf<List<LyricLine>?>(null) }
    var loading by remember(item.mediaId) { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var offsetMs by remember(item.mediaId) { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()

    // La pantalla no se apaga mientras se canta.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(item.mediaId) {
        loading = true
        // El reproductor solo se puede consultar desde el hilo principal: se lee antes de ir a internet.
        val dur = ui.controller?.duration?.takeIf { it > 0 }?.div(1000) ?: song.durationSec
        lines = withContext(Dispatchers.IO) {
            runCatching { Lyrics.find(song.title, song.artist, item.karaokeQuery, dur) }.getOrNull()
        }
        loading = false
    }

    LaunchedEffect(item.mediaId) {
        while (true) {
            ui.controller?.let { positionMs = it.currentPosition }
            delay(200)
        }
    }

    val current = lines?.let { l ->
        val t = positionMs + offsetMs
        l.indexOfLast { it.timeMs <= t }
    } ?: -1

    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = null)
                Text(" Buscar otra")
            }
        }

        // Ajuste fino por si la versión karaoke no coincide exactamente con la letra.
        if (lines != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { offsetMs -= 500 }) { Text("Letra antes") }
                Text(
                    if (offsetMs == 0L) "sincronizada" else "%+.1f s".format(offsetMs / 1000f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { offsetMs = 0 },
                )
                TextButton(onClick = { offsetMs += 500 }) { Text("Letra después") }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                lines == null -> CenterMessage(
                    "No encontré la letra sincronizada de esta canción.\n\nLa música sigue sonando; probá con otra versión desde «Buscar otra»."
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 120.dp),
                ) {
                    itemsIndexed(lines!!) { i, line ->
                        val active = i == current
                        Text(
                            line.text.ifBlank { "♪" },
                            fontSize = if (active) 30.sp else 22.sp,
                            lineHeight = if (active) 36.sp else 28.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                active -> MaterialTheme.colorScheme.primary
                                i < current -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
