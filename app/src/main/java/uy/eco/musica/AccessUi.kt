package uy.eco.musica

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.style.TextOverflow
import java.io.IOException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DOWNLOAD_LINK =
    "https://github.com/alfonsocarabajal-pixel/Alfa-music/releases/latest/download/AlfaMusic.apk"

/** Pantalla que pide el código de invitación antes de usar la app. */
@Composable
fun ActivationScreen(access: Access, notice: String?, onActivated: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(notice) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        if (input.isBlank() || busy) return
        keyboard?.hide()
        busy = true
        error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { access.activate(input) }
            busy = false
            when (r) {
                Access.Result.Ok -> onActivated()
                Access.Result.Offline -> error = "Sin conexión. Conectate a internet y probá de nuevo."
                is Access.Result.Rejected -> error = r.message
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text("Alfa Music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ingresá el código de invitación que te pasaron.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.uppercase().take(30) },
                placeholder = { Text("Ej.: LUNA27") },
                singleLine = true,
                enabled = !busy,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { submit() }, enabled = input.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                else Text("Activar")
            }
        }
    }
}

/** Grupos del administrador, cargados desde Firebase. */
class GroupsState(private val access: Access) {
    var items by mutableStateOf<List<Group>>(emptyList())
        private set

    suspend fun refresh() {
        withContext(Dispatchers.IO) { runCatching { access.listGroups() } }.onSuccess { items = it }
    }

    fun name(id: String) = items.firstOrNull { it.id == id }?.name ?: if (id == Access.MAIN_GROUP) "Principal" else "…"

    suspend fun create(name: String): Group? =
        withContext(Dispatchers.IO) { runCatching { access.createGroup(name) } }.getOrNull()
            ?.also { g -> items = items + g }
}

/** Selector de grupo en la pestaña Playlists (solo el administrador lo ve). */
@Composable
fun GroupSwitcher(access: Access, onChanged: () -> Unit) {
    val groups = remember { GroupsState(access) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { groups.refresh() }

    Box {
        TextButton(onClick = { open = true }) {
            Text(
                "Grupo: ${groups.name(Cloud.groupId)}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar de grupo")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            groups.items.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.name) },
                    leadingIcon = {
                        if (g.id == Cloud.groupId) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        if (g.id != Cloud.groupId) {
                            access.groupId = g.id
                            onChanged()
                        }
                    },
                )
            }
        }
    }
}

/** Lista para elegir un grupo, con la opción de crear uno nuevo escribiendo el nombre. */
@Composable
private fun GroupChooser(
    groups: GroupsState,
    selected: String,
    newName: String,
    onSelect: (String) -> Unit,
    onNewName: (String) -> Unit,
) {
    Column {
        groups.items.forEach { g ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(g.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == g.id, onClick = { onSelect(g.id) })
                Text(g.name)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(NEW_GROUP) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == NEW_GROUP, onClick = { onSelect(NEW_GROUP) })
            Text("Nuevo grupo…")
        }
        if (selected == NEW_GROUP) {
            OutlinedTextField(
                value = newName,
                onValueChange = { onNewName(it.take(40)) },
                label = { Text("Nombre del grupo nuevo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val NEW_GROUP = "__nuevo__"

/** Pestaña del administrador: crear, compartir, mover y revocar invitaciones. */
@Composable
fun InvitesScreen(access: Access, notify: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups = remember { GroupsState(access) }
    var invites by remember { mutableStateOf<List<Invite>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<Invite?>(null) }
    var revoking by remember { mutableStateOf<Invite?>(null) }
    var moving by remember { mutableStateOf<Invite?>(null) }
    var latest by remember { mutableStateOf<Int?>(null) }

    fun refresh() = scope.launch {
        loading = true
        loadError = null
        groups.refresh()
        if (latest == null) latest = withContext(Dispatchers.IO) { Updater.latestVersion() }
        withContext(Dispatchers.IO) { runCatching { access.listInvites() } }
            .onSuccess { invites = it }
            .onFailure { loadError = "No se pudieron cargar las invitaciones. Revisá la conexión." }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Invitaciones", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Solo vos ves esta pestaña",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
            Button(onClick = { creating = true }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Nueva")
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading && invites.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                loadError != null && invites.isEmpty() -> CenterMessage(loadError.orEmpty())
                invites.isEmpty() -> CenterMessage("Todavía no creaste invitaciones.\n\nTocá «Nueva» para crear el código de un amigo.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(invites, key = { it.code }) { inv ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(inv.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${inv.pretty} · ${groups.name(inv.group)}" + if (inv.karaoke) " · 🎤 Karaoke" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    status(inv),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        inv.revoked > 0 -> MaterialTheme.colorScheme.error
                                        inv.activated > 0 -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                if (inv.revoked == 0L && inv.activated > 0) {
                                    val outdated = latest != null && inv.version in 1 until latest!!
                                    Text(
                                        usage(inv, latest),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (outdated || inv.version == 0) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (inv.revoked == 0L && inv.activated == 0L) {
                                IconButton(onClick = { share(context, inv) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Compartir código")
                                }
                            }
                            if (inv.revoked == 0L) {
                                IconButton(onClick = {
                                    val enable = !inv.karaoke
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            runCatching { access.setKaraoke(inv.code, enable) }
                                        }.isSuccess
                                        if (ok) {
                                            invites = invites.map { if (it.code == inv.code) it.copy(karaoke = enable) else it }
                                            notify(
                                                if (enable) "Karaoke activado para ${inv.name} (lo ve la próxima vez que abra la app)"
                                                else "Karaoke desactivado para ${inv.name}"
                                            )
                                        } else notify("No se pudo cambiar. Revisá la conexión.")
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = if (inv.karaoke) "Desactivar karaoke" else "Activar karaoke",
                                        tint = if (inv.karaoke) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    )
                                }
                                IconButton(onClick = { moving = inv }) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "Cambiar de grupo")
                                }
                                IconButton(onClick = { revoking = inv }) {
                                    Icon(Icons.Default.Block, contentDescription = "Revocar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var group by remember { mutableStateOf(Cloud.groupId) }
        var newGroup by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val valid = name.isNotBlank() && (group != NEW_GROUP || newGroup.isNotBlank())
        AlertDialog(
            onDismissRequest = { if (!busy) creating = false },
            title = { Text("Nueva invitación") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("¿Para quién es?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Grupo", style = MaterialTheme.typography.labelLarge)
                    GroupChooser(groups, group, newGroup, onSelect = { group = it }, onNewName = { newGroup = it })
                }
            },
            confirmButton = {
                TextButton(enabled = valid && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val groupId = if (group == NEW_GROUP) groups.create(newGroup)?.id else group
                        val r = if (groupId == null) Result.failure<Invite>(IOException())
                        else withContext(Dispatchers.IO) { runCatching { access.createInvite(name, groupId) } }
                        busy = false
                        r.onSuccess {
                            creating = false
                            created = it
                            invites = listOf(it) + invites
                        }.onFailure { notify("No se pudo crear el código. Revisá la conexión.") }
                    }
                }) { Text(if (busy) "Creando…" else "Crear código") }
            },
            dismissButton = { TextButton(onClick = { creating = false }, enabled = !busy) { Text("Cancelar") } },
        )
    }

    moving?.let { inv ->
        var group by remember(inv.code) { mutableStateOf(inv.group) }
        var newGroup by remember(inv.code) { mutableStateOf("") }
        var busy by remember(inv.code) { mutableStateOf(false) }
        val valid = group != inv.group && (group != NEW_GROUP || newGroup.isNotBlank())
        AlertDialog(
            onDismissRequest = { if (!busy) moving = null },
            title = { Text("Grupo de ${inv.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GroupChooser(groups, group, newGroup, onSelect = { group = it }, onNewName = { newGroup = it })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Se aplica la próxima vez que abra la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val groupId = if (group == NEW_GROUP) groups.create(newGroup)?.id else group
                        val ok = groupId != null &&
                            withContext(Dispatchers.IO) { runCatching { access.moveInvite(inv.code, groupId) } }.isSuccess
                        busy = false
                        if (ok) {
                            moving = null
                            invites = invites.map { if (it.code == inv.code) it.copy(group = groupId!!) else it }
                            notify("${inv.name} ahora está en ${groups.name(groupId!!)}")
                        } else notify("No se pudo cambiar el grupo. Revisá la conexión.")
                    }
                }) { Text(if (busy) "Guardando…" else "Cambiar") }
            },
            dismissButton = { TextButton(onClick = { moving = null }, enabled = !busy) { Text("Cancelar") } },
        )
    }

    created?.let { inv ->
        AlertDialog(
            onDismissRequest = { created = null },
            title = { Text("Código para ${inv.name}") },
            text = {
                Column {
                    Text(
                        inv.pretty,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Grupo: ${groups.name(inv.group)}. Sirve para un solo teléfono. Mandáselo junto con el link de descarga.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { share(context, inv); created = null }) { Text("Compartir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Código Alfa Music", inv.pretty))
                    notify("Código copiado")
                    created = null
                }) { Text("Copiar") }
            },
        )
    }

    revoking?.let { inv ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text("¿Revocar el acceso de ${inv.name}?") },
            text = { Text("La próxima vez que abra la app con internet, le va a pedir un código nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { access.revoke(inv.code) } }.isSuccess
                        if (ok) {
                            notify("Acceso revocado: ya no aparece en la lista")
                            refresh()
                        } else notify("No se pudo revocar. Revisá la conexión.")
                    }
                }) { Text("Revocar") }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Cancelar") } },
        )
    }
}

private fun usage(inv: Invite, latest: Int?): String {
    val version = when {
        inv.version == 0 -> "Versión desconocida (todavía no actualizó)"
        latest != null && inv.version < latest -> "Versión ${inv.version} · desactualizada (la última es la $latest)"
        else -> "Versión ${inv.version} · al día"
    }
    if (inv.seen == 0L) return version
    val days = ((System.currentTimeMillis() - inv.seen) / 86_400_000L).toInt()
    val seen = when {
        days <= 0 -> "usó la app hoy"
        days == 1 -> "usó la app ayer"
        days < 30 -> "usó la app hace $days días"
        else -> "usó la app el " + SimpleDateFormat("d/M/yyyy", Locale("es", "UY")).format(Date(inv.seen))
    }
    return "$version · $seen"
}

private fun status(inv: Invite): String {
    val fmt = SimpleDateFormat("d/M/yyyy", Locale("es", "UY"))
    return when {
        inv.revoked > 0 -> "Revocado el ${fmt.format(Date(inv.revoked))}"
        inv.activated > 0 -> "Activado en ${inv.model.ifBlank { "un teléfono" }} el ${fmt.format(Date(inv.activated))}"
        else -> "Pendiente · creado el ${fmt.format(Date(inv.created))}"
    }
}

private fun share(context: Context, inv: Invite) {
    val text = "Te invito a Alfa Music 🎵\n\n1. Descargá la app: $DOWNLOAD_LINK\n" +
        "2. Al abrirla, ingresá este código: ${inv.pretty}\n\n(Sirve para un solo teléfono)"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Compartir invitación").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
