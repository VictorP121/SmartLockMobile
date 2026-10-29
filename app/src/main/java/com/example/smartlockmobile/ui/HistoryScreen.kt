package com.example.smartlockmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartlockmobile.SmartLockViewModel
import com.example.smartlockmobile.data.EventType
import com.example.smartlockmobile.data.Role
import com.example.smartlockmobile.data.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: SmartLockViewModel, user: User, modifier: Modifier = Modifier) {
    val events by vm.events.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val isAdmin = user.role == Role.ADMIN

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (isAdmin) "Todos los usuarios" else "Solo tus eventos",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isAdmin) {
                OutlinedButton(onClick = { confirmClear = true }, enabled = events.isNotEmpty()) { Text("Borrar") }
            }
        }

        if (events.isEmpty()) {
            Text("Aún no hay eventos.", modifier = Modifier.padding(top = 24.dp))
        }

        LazyColumn(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(EventType.label(e.type), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${e.username} · ${format.format(Date(e.timestamp))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (e.detail.isNotBlank()) {
                            Text(e.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Borrar historial") },
            text = { Text("Se eliminarán todos los eventos registrados. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
}
