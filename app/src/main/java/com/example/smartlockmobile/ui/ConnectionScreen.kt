package com.example.smartlockmobile.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smartlockmobile.DeviceRole
import com.example.smartlockmobile.SmartLockViewModel
import com.example.smartlockmobile.bluetooth.LinkState
import com.example.smartlockmobile.data.Role
import com.example.smartlockmobile.data.User

private fun LinkState.label(): String = when (this) {
    is LinkState.Idle -> "Sin conexión"
    is LinkState.Listening -> "Esperando que el otro teléfono se conecte…"
    is LinkState.Connecting -> "Conectando con $name…"
    is LinkState.Connected -> "Conectado con $name"
    is LinkState.Failed -> "Error: $reason"
}

@Composable
fun ConnectionScreen(vm: SmartLockViewModel, user: User, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val role by vm.deviceRole.collectAsState()
    val linkState by vm.link.state.collectAsState()
    val pinConfigured by vm.pinConfigured.collectAsState()

    var refresh by remember { mutableStateOf(0) }
    var permissionOk by remember { mutableStateOf(vm.link.hasPermission()) }
    var btEnabled by remember { mutableStateOf(vm.link.isEnabled) }
    var pinInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionOk = granted
        refresh++
    }
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        btEnabled = vm.link.isEnabled
        refresh++
    }

    val ready = vm.link.isSupported && permissionOk && btEnabled
    val connected = linkState is LinkState.Connected
    val devices = remember(refresh, permissionOk, btEnabled) { vm.link.pairedDevices() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Conexión Bluetooth", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Instala la app en dos teléfonos: uno hace de cerradura (simula el Arduino) y el otro de control remoto.",
            style = MaterialTheme.typography.bodyMedium
        )

        // ---- Rol ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Rol de este teléfono", fontWeight = FontWeight.SemiBold)
                RoleOption("Cerradura (recibe órdenes)", role == DeviceRole.LOCK) { vm.setDeviceRole(DeviceRole.LOCK) }
                RoleOption("Control remoto (envía órdenes)", role == DeviceRole.CONTROLLER) {
                    vm.setDeviceRole(DeviceRole.CONTROLLER)
                }
            }
        }

        // ---- Estado y requisitos ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estado", fontWeight = FontWeight.SemiBold)
                Text(linkState.label())

                when {
                    !vm.link.isSupported ->
                        Text("Este equipo no tiene Bluetooth (en el emulador no se puede probar).",
                            color = MaterialTheme.colorScheme.error)
                    !permissionOk -> Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }) { Text("Conceder permiso de Bluetooth") }
                    !btEnabled -> Button(onClick = {
                        enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }) { Text("Activar Bluetooth") }
                }

                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) { Text("Emparejar teléfonos (Ajustes)") }

                if (connected || linkState is LinkState.Listening || linkState is LinkState.Connecting) {
                    OutlinedButton(onClick = { vm.disconnectLink() }) {
                        Text(if (connected) "Desconectar" else "Cancelar")
                    }
                }
            }
        }

        // ---- Específico por rol ----
        if (role == DeviceRole.LOCK) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PIN de la cerradura", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (pinConfigured) "PIN configurado (se guarda cifrado con hash)."
                        else "Aún no hay PIN: el control remoto no podrá abrir hasta configurarlo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (user.role == Role.ADMIN) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it.filter(Char::isDigit).take(8) },
                            label = { Text("Nuevo PIN (4 a 8 dígitos)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { vm.savePin(pinInput); pinInput = "" },
                            enabled = pinInput.length >= 4
                        ) { Text("Guardar PIN") }
                    } else {
                        Text("Solo el administrador puede cambiar el PIN.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(
                onClick = { vm.startListening() },
                enabled = ready && !connected && linkState !is LinkState.Listening,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Esperar conexión del control remoto") }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Dispositivos emparejados", fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = {
                            permissionOk = vm.link.hasPermission()
                            btEnabled = vm.link.isEnabled
                            refresh++
                        }) { Text("Actualizar") }
                    }
                    if (!ready) {
                        Text("Activa Bluetooth y concede el permiso para ver la lista.",
                            style = MaterialTheme.typography.bodySmall)
                    } else if (devices.isEmpty()) {
                        Text("No hay dispositivos emparejados. Empareja el teléfono-cerradura desde Ajustes.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    devices.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontWeight = FontWeight.Medium)
                                Text(d.address, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { vm.connectTo(d.address) },
                                enabled = ready && !connected && linkState !is LinkState.Connecting
                            ) { Text("Conectar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleOption(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = SemanticsRole.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
    }
}
