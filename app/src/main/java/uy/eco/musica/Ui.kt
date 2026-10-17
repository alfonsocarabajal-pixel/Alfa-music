package uy.eco.musica

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun EcoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun EcoScreen(ui: PlayerUi) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>("Buscá una canción o un artista") }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        showQueue = false
        loading = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Repo.search(q) } }
            loading = false
            result
                .onSuccess {
                    results = it
                    if (it.isEmpty()) message = "Sin resultados"
                }
                .onFailure {
                    results = emptyList()
                    message = "No se pudo buscar. Revisá la conexión o probá de nuevo."
                }
        }
    }

    BackHandler(enabled = showQueue) { showQueue = false }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Canciones o artistas") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Borrar")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
            }
        },
        bottomBar = {
            if (ui.current != null) {
                MiniPlayer(ui, queueOpen = showQueue, onQueue = { showQueue = !showQueue })
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                showQueue -> QueueList(ui)
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                results.isEmpty() -> Text(
                    message.orEmpty(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { song ->
                        SongRow(
                            thumb = song.thumb,
                            title = song.title,
                            subtitle = listOf(song.artist, formatTime(song.durationSec))
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            highlighted = ui.current?.mediaId == song.id,
                            onClick = { ui.playNow(song) },
                        ) {
                            IconButton(onClick = { ui.enqueue(song) }) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar a la cola")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    thumb: String?,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(thumb, 52.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action()
    }
}

@Composable
private fun MiniPlayer(ui: PlayerUi, queueOpen: Boolean, onQueue: () -> Unit) {
    val item = ui.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(ui.controller, item.mediaId) {
        while (true) {
            ui.controller?.let { c ->
                val d = c.duration
                progress = if (d > 0) (c.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            }
            delay(500)
        }
    }

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(item.mediaMetadata.artworkUri?.toString(), 44.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        item.mediaMetadata.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.mediaMetadata.artist?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ui.buffering) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { ui.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                }
                IconButton(onClick = { ui.toggle() }) {
                    Icon(
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ui.isPlaying) "Pausa" else "Reproducir",
                    )
                }
                IconButton(onClick = { ui.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                }
                IconButton(onClick = onQueue) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Cola",
                        tint = if (queueOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueList(ui: PlayerUi) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cola", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${ui.queue.size} canciones" + if (ui.shuffle) " · Aleatorio" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { ui.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = if (ui.shuffle) "Desactivar aleatorio" else "Activar aleatorio",
                        tint = if (ui.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(ui.queue) { entry ->
            val i = entry.index
            val item = entry.item
            val radio = item.mediaMetadata.extras?.getBoolean(EXTRA_RADIO) == true
            SongRow(
                thumb = item.mediaMetadata.artworkUri?.toString(),
                title = item.mediaMetadata.title?.toString().orEmpty(),
                subtitle = listOfNotNull(
                    item.mediaMetadata.artist?.toString(),
                    if (radio) "Radio" else null,
                ).joinToString(" · "),
                highlighted = i == ui.index,
                onClick = { ui.playAt(i) },
            ) {
                if (i != ui.index) {
                    IconButton(onClick = { ui.remove(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar")
                    }
                }
            }
        }
    }
}

/** Cargador de imágenes mínimo, sin librerías: descarga, achica y guarda en memoria. */
private object Images {
    private val cache = LruCache<String, ImageBitmap>(200)

    fun cached(url: String): ImageBitmap? = cache.get(url)

    fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val raw = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val max = 256
            val scaled = if (raw.width > max || raw.height > max) {
                val ratio = max.toFloat() / maxOf(raw.width, raw.height)
                Bitmap.createScaledBitmap(
                    raw, (raw.width * ratio).toInt().coerceAtLeast(1),
                    (raw.height * ratio).toInt().coerceAtLeast(1), true
                )
            } else raw
            scaled.asImageBitmap().also { cache.put(url, it) }
        } finally {
            conn.disconnect()
        }
    }
}

@Composable
private fun Thumb(url: String?, size: Dp) {
    val image by produceState(initialValue = url?.let { Images.cached(it) }, url) {
        if (url != null && value == null) {
            value = withContext(Dispatchers.IO) { runCatching { Images.load(url) }.getOrNull() }
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        image?.let {
            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun formatTime(seconds: Long): String =
    if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)
