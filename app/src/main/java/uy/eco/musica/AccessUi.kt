package uy.eco.musica

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
                onValueChange = { input = it.uppercase().take(24) },
                placeholder = { Text("ALFA-XXXX-XXXX") },
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

/** Pestaña del administrador: crear, compartir y revocar invitaciones. */
@Composable
fun InvitesScreen(access: Access, notify: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var invites by remember { mutableStateOf<List<Invite>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<Invite?>(null) }
    var revoking by remember { mutableStateOf<Invite?>(null) }

    fun refresh() = scope.launch {
        loading = true
        loadError = null
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
                                    inv.pretty,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
                            }
                            if (inv.revoked == 0L && inv.activated == 0L) {
                                IconButton(onClick = { share(context, inv) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Compartir código")
                                }
                            }
                            if (inv.revoked == 0L) {
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
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) creating = false },
            title = { Text("Nueva invitación") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("¿Para quién es?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { access.createInvite(name) } }
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
                        "Sirve para un solo teléfono. Mandáselo junto con el link de descarga.",
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
                            notify("Acceso revocado")
                            refresh()
                        } else notify("No se pudo revocar. Revisá la conexión.")
                    }
                }) { Text("Revocar") }
            },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text("Cancelar") } },
        )
    }
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
