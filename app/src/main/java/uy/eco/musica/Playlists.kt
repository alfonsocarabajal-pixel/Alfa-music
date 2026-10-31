package uy.eco.musica

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado de las playlists compartidas del grupo. */
class SharedLists {
    var items by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadedOnce by mutableStateOf(false)
        private set

    private var loadedGroup = ""

    fun byId(id: String) = items.firstOrNull { it.id == id }

    suspend fun refresh() {
        if (!Cloud.enabled) return
        if (Cloud.groupId != loadedGroup) {
            // Cambió el grupo: no se muestran las playlists del grupo anterior mientras carga.
            items = emptyList()
            loadedOnce = false
            loadedGroup = Cloud.groupId
        }
        loading = true
        error = null
        withContext(Dispatchers.IO) { runCatching { Cloud.list() } }
            .onSuccess { items = it; loadedOnce = true }
            .onFailure {
                ErrorLog.report("Cargar playlists", ErrorLog.describe(it))
                error = "No se pudieron cargar las playlists. Revisá la conexión."
            }
        loading = false
    }

    /** Guarda y actualiza la lista local. Devuelve null si falló. */
    suspend fun save(p: Playlist): Playlist? {
        val saved = withContext(Dispatchers.IO) { runCatching { Cloud.save(p) } }
            .onFailure { ErrorLog.report("Guardar playlist", "«${p.name}» · ${ErrorLog.describe(it)}") }
            .getOrNull()
            ?: return null
        items = (listOf(saved) + items.filter { it.id != saved.id }).sortedByDescending { it.updated }
        return saved
    }

    enum class AddResult { ADDED, ALREADY_THERE, FAILED }

    /**
     * Suma una canción al final de una playlist. Antes trae la versión actual del servidor,
     * así no se pisan cambios que otro haya hecho mientras tanto.
     */
    suspend fun addSong(id: String, song: Song): AddResult {
        val fresh = withContext(Dispatchers.IO) { runCatching { Cloud.get(id) } }.getOrNull()
            ?: return AddResult.FAILED
        if (fresh.songs.any { it.id == song.id }) return AddResult.ALREADY_THERE
        return if (save(fresh.copy(songs = fresh.songs + song)) != null) AddResult.ADDED else AddResult.FAILED
    }

    suspend fun delete(id: String): Boolean {
        val ok = withContext(Dispatchers.IO) { runCatching { Cloud.delete(id) } }
            .onFailure { ErrorLog.report("Borrar playlist", ErrorLog.describe(it)) }
            .isSuccess
        if (ok) items = items.filter { it.id != id }
        return ok
    }
}

@Composable
fun PlaylistsList(
    lists: SharedLists,
    current: String,
    access: Access,
    onGroupChanged: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Cloud.groupId) { lists.refresh() }

    if (!Cloud.enabled) {
        CenterMessage("Las playlists compartidas todavía no están configuradas.")
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (access.isAdmin) {
                Box(Modifier.weight(1f)) { GroupSwitcher(access, onGroupChanged) }
            } else {
                Text("Playlists del grupo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = { scope.launch { lists.refresh() } }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                lists.loading && lists.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                lists.error != null && lists.items.isEmpty() -> CenterMessage(lists.error.orEmpty())

                lists.items.isEmpty() && lists.loadedOnce -> CenterMessage(
                    "Todavía no hay playlists.\n\nArmá una cola con las canciones que quieras, " +
                        "abrila con el botón de abajo a la derecha y guardala con 💾."
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(lists.items, key = { it.id }) { p ->
                        SongRow(
                            thumb = p.songs.firstOrNull()?.thumb,
                            title = p.name,
                            subtitle = listOfNotNull(
                                "${p.songs.size} canciones",
                                p.author.ifBlank { null }?.let { "de $it" },
                            ).joinToString(" · "),
                            highlighted = p.id == current,
                            onClick = { onOpen(p.id) },
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetail(
    id: String,
    lists: SharedLists,
    ui: PlayerUi,
    access: Access,
    onBack: () -> Unit,
    notify: (String) -> Unit,
) {
    val p = lists.byId(id)
    if (p == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }

    fun update(changed: Playlist, okMessage: String? = null) = scope.launch {
        val saved = lists.save(changed)
        if (saved == null) notify("No se pudo guardar. Revisá la conexión.")
        else {
            ui.onPlaylistChanged(saved)
            okMessage?.let(notify)
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                "${p.songs.size} canciones",
                                p.author.ifBlank { null }?.let { "de $it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (access.isAdmin) {
                        IconButton(onClick = { copying = true }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar a otro grupo")
                        }
                    }
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Cambiar nombre")
                    }
                    IconButton(onClick = { deleting = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar playlist")
                    }
                }
                Row(
                    Modifier.padding(start = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { ui.playPlaylist(p) }, enabled = p.songs.isNotEmpty()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reproducir")
                    }
                    OutlinedButton(onClick = { ui.playPlaylist(p, shuffled = true) }, enabled = p.songs.isNotEmpty()) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Aleatorio")
                    }
                }
            }
        }
        if (p.songs.isEmpty()) {
            item { CenterMessage("Esta playlist está vacía.") }
        }
        itemsIndexed(p.songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
            SongRow(
                thumb = s.thumb,
                title = s.title,
                subtitle = listOf(s.artist, formatTime(s.durationSec)).filter { it.isNotBlank() }.joinToString(" · "),
                highlighted = ui.loadedId == p.id && ui.current?.mediaId == s.id,
                onClick = { ui.playPlaylist(p, start = i) },
            ) {
                IconButton(onClick = {
                    update(p.copy(songs = p.songs.filterIndexed { j, _ -> j != i }), "Canción quitada")
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar de la playlist")
                }
            }
        }
    }

    if (copying) {
        CopyToGroupDialog(p, access, onDismiss = { copying = false }, notify = notify)
    }

    if (renaming) {
        NameDialog(
            title = "Cambiar nombre",
            initial = p.name,
            confirm = "Guardar",
            onDismiss = { renaming = false },
        ) { name ->
            renaming = false
            update(p.copy(name = name), "Nombre cambiado")
        }
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("¿Borrar «${p.name}»?") },
            text = { Text("Se borra para todo el grupo y no se puede recuperar.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        if (lists.delete(p.id)) {
                            ui.onPlaylistChanged(null, deletedId = p.id)
                            notify("Playlist borrada")
                            onBack()
                        } else notify("No se pudo borrar. Revisá la conexión.")
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
        )
    }
}

/** Copiar una playlist a otro grupo (solo el administrador). */
@Composable
private fun CopyToGroupDialog(p: Playlist, access: Access, onDismiss: () -> Unit, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val groups = remember { GroupsState(access) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { groups.refresh() }
    val others = groups.items.filter { it.id != Cloud.groupId }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Copiar «${p.name}» a…") },
        text = {
            Column {
                if (others.isEmpty()) {
                    Text(
                        "No hay otros grupos. Se crean desde Invitaciones, al invitar a alguien.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                others.forEach { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                busy = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching { Cloud.copyToGroup(p, g.id) }
                                    }.isSuccess
                                    busy = false
                                    if (ok) {
                                        notify("«${p.name}» copiada a ${g.name}")
                                        onDismiss()
                                    } else notify("No se pudo copiar. Revisá la conexión.")
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                    ) { Text(g.name) }
                }
                if (busy) CircularProgressIndicator(Modifier.padding(8.dp))
                Text(
                    "Se crea una copia independiente: si después cambian una, la otra no se toca.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

/** Elegir a qué playlist del grupo sumar una canción (o crear una nueva con ella). */
@Composable
fun AddToPlaylistDialog(
    song: Song,
    ui: PlayerUi,
    lists: SharedLists,
    onDismiss: () -> Unit,
    notify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var author by remember { mutableStateOf(ui.prefs.author) }
    val askAuthor = remember { ui.prefs.author.isBlank() }

    LaunchedEffect(Unit) { if (!lists.loadedOnce) lists.refresh() }

    fun addTo(p: Playlist) {
        busy = true
        scope.launch {
            val r = lists.addSong(p.id, song)
            busy = false
            when (r) {
                SharedLists.AddResult.ADDED -> {
                    lists.byId(p.id)?.let { ui.onPlaylistChanged(it) }
                    notify("Agregada a «${p.name}»")
                    onDismiss()
                }
                SharedLists.AddResult.ALREADY_THERE -> notify("Ya estaba en «${p.name}»")
                SharedLists.AddResult.FAILED -> notify("No se pudo agregar. Revisá la conexión.")
            }
        }
    }

    fun createWithSong() {
        busy = true
        if (author.isNotBlank()) ui.prefs.author = author
        scope.launch {
            val saved = lists.save(Playlist("", newName.trim(), author.trim(), 0, listOf(song)))
            busy = false
            if (saved == null) notify("No se pudo crear la playlist. Revisá la conexión.")
            else {
                notify("Playlist «${saved.name}» creada")
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Agregar a playlist") },
        text = {
            Column {
                Text(
                    "${song.title}" + if (song.artist.isNotBlank()) " · ${song.artist}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(80) },
                        label = { Text("Nombre de la playlist nueva") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (askAuthor) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it.take(40) },
                            label = { Text("Tu nombre (para el grupo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        item {
                            TextButton(onClick = { creating = true }, enabled = !busy) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Nueva playlist…")
                            }
                        }
                        if (lists.loading && lists.items.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        items(lists.items, key = { it.id }) { p ->
                            val already = p.songs.any { it.id == song.id }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy && !already) { addTo(p) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (already) "Ya la tiene" else "${p.songs.size} canciones",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (already) Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(enabled = newName.isNotBlank() && !busy, onClick = { createWithSong() }) {
                    Text(if (busy) "Creando…" else "Crear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (creating) creating = false else onDismiss() }, enabled = !busy) {
                Text(if (creating) "Volver" else "Cancelar")
            }
        },
    )
}

/** Diálogo para guardar la cola actual como playlist del grupo. */
@Composable
fun SaveQueueDialog(
    ui: PlayerUi,
    lists: SharedLists,
    onDismiss: () -> Unit,
    notify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loadedName = ui.loadedName
    var name by remember { mutableStateOf(loadedName) }
    var author by remember { mutableStateOf(ui.prefs.author) }
    val askAuthor = remember { ui.prefs.author.isBlank() }
    var saving by remember { mutableStateOf(false) }
    val updating = loadedName.isNotBlank() && ui.loadedId.isNotBlank() &&
        name.trim().equals(loadedName.trim(), ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (updating) "Actualizar playlist" else "Guardar como playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Nombre de la playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (askAuthor) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it.take(40) },
                        label = { Text("Tu nombre (para el grupo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        updating -> "Se reemplaza «$loadedName» con la cola actual (sin las sugerencias de la radio)."
                        loadedName.isNotBlank() -> "Con otro nombre se crea una playlist nueva. " +
                            "Para actualizar «$loadedName», dejá ese nombre."
                        else -> "La van a ver todos en la pestaña Playlists."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    if (author.isNotBlank()) ui.prefs.author = author
                    val songs = ui.songsToSave()
                    val playlist = Playlist(
                        id = if (updating) ui.loadedId else "",
                        name = name.trim(),
                        author = if (updating) ui.prefs.loadedAuthor.ifBlank { author } else author.trim(),
                        updated = 0,
                        songs = songs,
                    )
                    scope.launch {
                        val saved = lists.save(playlist)
                        saving = false
                        if (saved == null) notify("No se pudo guardar. Revisá la conexión.")
                        else {
                            ui.markLoaded(saved)
                            notify(if (updating) "Playlist actualizada" else "Playlist «${saved.name}» guardada")
                            onDismiss()
                        }
                    }
                },
            ) { Text(if (saving) "Guardando…" else if (updating) "Actualizar" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
