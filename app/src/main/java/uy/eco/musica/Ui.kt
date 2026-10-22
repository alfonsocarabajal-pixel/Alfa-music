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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
fun EcoScreen(ui: PlayerUi, access: Access) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>("Buscá una canción o un artista") }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = Buscar, 1 = Playlists, 2 = Invitaciones
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<Song?>(null) }
    val onAdd: ((Song) -> Unit)? = if (Cloud.enabled) ({ addTarget = it }) else null
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    val lists = remember { SharedLists() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastSearched by rememberSaveable { mutableStateOf("") }
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    // Al abrir la app con una cola guardada, se muestra directamente.
    LaunchedEffect(ui.queue.isNotEmpty()) {
        if (!autoOpened && ui.queue.isNotEmpty()) {
            autoOpened = true
            showQueue = true
        }
    }

    // Sugerencias mientras se escribe (con una pausa corta para no consultar en cada tecla).
    LaunchedEffect(query, focused) {
        val q = query.trim()
        if (!focused || q.length < 2 || q.equals(lastSearched, ignoreCase = true)) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = withContext(Dispatchers.IO) {
            runCatching { Repo.suggestions(q) }.getOrDefault(suggestions)
        }
    }

    fun search(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = q
        lastSearched = q
        suggestions = emptyList()
        focusManager.clearFocus()
        keyboard?.hide()
        showQueue = false
        tab = 0
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

    BackHandler(enabled = focused && suggestions.isNotEmpty()) { focusManager.clearFocus() }
    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(enabled = !showQueue && tab == 1 && openPlaylist != null) { openPlaylist = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(tonalElevation = 2.dp) {
              Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onFocusChanged { focused = it.isFocused },
                    placeholder = { Text("Canciones o artistas") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; lastSearched = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Borrar")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                // 0 = Buscar, 1 = Playlists, 2 = Invitaciones (solo admin), 3 = Karaoke (con permiso)
                val tabs = buildList {
                    add(0 to "Buscar")
                    add(1 to "Playlists")
                    if (access.showKaraoke) add(3 to "El karaoke de Diego")
                    if (access.isAdmin) add(2 to "Invitaciones")
                }
                val selected = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)
                val tabContent: @Composable () -> Unit = {
                    tabs.forEach { (id, label) ->
                        Tab(
                            selected = !showQueue && tab == id,
                            onClick = {
                                if (id == 1 && tab == 1 && !showQueue) openPlaylist = null
                                tab = id
                                showQueue = false
                            },
                            text = { Text(label, maxLines = 1) },
                        )
                    }
                }
                // Con el nombre largo del karaoke, las pestañas se deslizan para que no se corte.
                if (tabs.size > 3 || access.showKaraoke) ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) { tabContent() }
                else TabRow(selectedTabIndex = selected) { tabContent() }
                UpdateBanner()
              }
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
                focused && suggestions.isNotEmpty() -> SuggestionList(
                    options = suggestions,
                    onPick = { search(it) },
                )
                showQueue -> QueueList(ui, onSave = if (Cloud.enabled) ({ showSave = true }) else null, onAdd = onAdd)
                tab == 2 && access.isAdmin -> InvitesScreen(access, notify)
                tab == 3 && access.showKaraoke -> KaraokeScreen(ui)
                tab == 1 -> {
                    val id = openPlaylist
                    if (id == null) PlaylistsList(
                        lists,
                        current = ui.loadedId,
                        access = access,
                        onGroupChanged = { ui.markLoaded(null) },
                        onOpen = { openPlaylist = it },
                    )
                    else PlaylistDetail(id, lists, ui, onBack = { openPlaylist = null }, notify = notify)
                }
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
                            if (onAdd != null) {
                                IconButton(onClick = { onAdd(song) }) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Agregar a playlist")
                                }
                            }
                            IconButton(onClick = { ui.enqueue(song) }) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar a la cola")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSave) {
        SaveQueueDialog(ui, lists, onDismiss = { showSave = false }, notify = notify)
    }
    addTarget?.let { song ->
        AddToPlaylistDialog(song, ui, lists, onDismiss = { addTarget = null }, notify = notify)
    }
}

/** Aviso de versión nueva, con descarga e instalación dentro de la app. */
@Composable
private fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { release = withContext(Dispatchers.IO) { Updater.check() } }

    val r = release ?: return
    if (dismissed) return

    fun start() {
        if (!Updater.canInstall(context)) {
            note = "Activá «Permitir de esta fuente» y volvé a tocar Actualizar."
            Updater.openInstallPermission(context)
            return
        }
        note = null
        progress = 0f
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { Updater.downloadAndInstall(context, r) { p -> progress = p } }.isSuccess
            }
            if (!ok) {
                progress = null
                note = "No se pudo descargar. Revisá la conexión y probá de nuevo."
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (progress == null) "Hay una versión nueva de Alfa Music" else "Descargando la actualización…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (progress == null) {
                    TextButton(onClick = { dismissed = true }) { Text("Más tarde") }
                    TextButton(onClick = { start() }) { Text("Actualizar") }
                }
            }
            progress?.let { p ->
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 8.dp),
                )
            }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Lista de sugerencias debajo del buscador. */
@Composable
private fun SuggestionList(options: List<String>, onPick: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), tonalElevation = 1.dp) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(options) { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(text) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SongRow(
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
private fun QueueList(ui: PlayerUi, onSave: (() -> Unit)?, onAdd: ((Song) -> Unit)?) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (ui.loadedName.isNotBlank()) ui.loadedName else "Cola",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${ui.queue.size} canciones" + if (ui.shuffle) " · Aleatorio" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onSave != null && ui.queue.isNotEmpty()) {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar como playlist")
                    }
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
                if (onAdd != null) {
                    IconButton(onClick = { onAdd(item.toSong()) }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Agregar a playlist")
                    }
                }
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
internal fun Thumb(url: String?, size: Dp) {
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

internal fun formatTime(seconds: Long): String =
    if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)
