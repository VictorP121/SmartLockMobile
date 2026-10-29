package com.example.smartlockmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartlockmobile.DeviceRole
import com.example.smartlockmobile.LockState
import com.example.smartlockmobile.SmartLockViewModel
import com.example.smartlockmobile.bluetooth.LinkState
import com.example.smartlockmobile.data.User

private val GreenOpen = Color(0xFF2E7D32)   // LED verde del prototipo
private val RedClosed = Color(0xFFC62828)   // LED rojo del prototipo
private val GreyUnknown = Color(0xFF757575)

@Composable
fun PanelScreen(vm: SmartLockViewModel, user: User, modifier: Modifier = Modifier) {
    val role by vm.deviceRole.collectAsState()
    val lock by vm.lockState.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val linkState by vm.link.state.collectAsState()
    val stats by vm.stats.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }

    val connected = linkState is LinkState.Connected
    val isController = role == DeviceRole.CONTROLLER

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("SmartLock Mobile", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Hola, ${user.username} (${if (user.role.name == "ADMIN") "administrador" else "usuario"})",
            style = MaterialTheme.typography.bodyMedium
        )

        val (color, emoji, text) = when (lock) {
            LockState.OPEN -> Triple(GreenOpen, "🔓", "ABIERTA")
            LockState.CLOSED -> Triple(RedClosed, "🔒", "CERRADA")
            LockState.UNKNOWN -> Triple(GreyUnknown, "❔", "SIN DATOS")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(color)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 64.sp)
            Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                if (isController) "Cerradura remota" else "Esta cerradura (simulada)",
                color = Color.White
            )
        }

        if (blocked) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "⚠️ Acceso remoto bloqueado por intentos de PIN fallidos. Espera 30 segundos.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (isController) {
            // ----- Teléfono-control -----
            if (!connected) {
                Text(
                    "Sin conexión. Ve a la pestaña Conexión y conéctate al teléfono que hace de cerradura.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = { showPinDialog = true },
                enabled = connected && !blocked && lock != LockState.OPEN,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Abrir cerradura (requiere PIN)") }

            Button(
                onClick = { vm.remoteLock() },
                enabled = connected && lock != LockState.CLOSED,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cerrar cerradura") }

            OutlinedButton(
                onClick = { vm.remoteRefresh() },
                enabled = connected,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Consultar estado") }
        } else {
            // ----- Teléfono-cerradura -----
            Button(
                onClick = { vm.toggleLocal() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (lock == LockState.OPEN) "Cerrar cerradura (pulsador)" else "Abrir cerradura (pulsador)")
            }
            Text(
                if (connected) "Control remoto conectado: también puede abrir y cerrar."
                else "Modo demostración local. Ve a Conexión para recibir órdenes de otro teléfono.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        // Datos almacenados en SQLite
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Resumen registrado", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("Aperturas", stats.opens)
                    StatItem("Cierres", stats.closes)
                    StatItem("Fallos", stats.failures)
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                showPinDialog = false
                vm.remoteUnlock(pin)
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN de la cerradura") },
        text = {
            Column {
                Text("Ingresa el PIN configurado por el administrador en el teléfono-cerradura.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = pin.length >= 4) { Text("Abrir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
