package uy.eco.musica

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Solo para el administrador: los errores que informaron los teléfonos, el más nuevo arriba. */
@Composable
fun ErrorsScreen(onBack: () -> Unit, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<ErrorReport>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "UY")) }

    fun refresh() = scope.launch {
        loading = true
        failed = false
        withContext(Dispatchers.IO) { runCatching { ErrorLog.list() } }
            .onSuccess { reports = it }
            .onFailure { failed = true }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Column(Modifier.weight(1f)) {
                Text("Errores de la app", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lo que falló en los teléfonos de cada uno",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
            if (reports.isNotEmpty()) {
                IconButton(onClick = { clearing = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar todos")
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                loading && reports.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                failed && reports.isEmpty() -> Text(
                    "No se pudieron cargar los errores. Revisá la conexión.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reports.isEmpty() -> Text(
                    "Sin errores. 🎉",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(reports, key = { it.id }) { r ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "${r.kind} · ${r.who}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${fmt.format(Date(r.time))} · ${r.model} · versión ${r.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(r.detail, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("¿Borrar los ${reports.size} errores?") },
            text = { Text("Se borran de la lista. Si vuelven a pasar, aparecen de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    clearing = false
                    val ids = reports.map { it.id }
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { ErrorLog.delete(ids) } }.isSuccess
                        if (ok) {
                            reports = emptyList()
                            notify("Errores borrados")
                        } else notify("No se pudieron borrar. Revisá la conexión.")
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancelar") } },
        )
    }
}
