package com.example.smartlockmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartlockmobile.SmartLockViewModel
import com.example.smartlockmobile.data.Role
import com.example.smartlockmobile.data.User

@Composable
fun AccountScreen(vm: SmartLockViewModel, user: User, modifier: Modifier = Modifier) {
    val users by vm.users.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.username, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (user.role == Role.ADMIN) "Rol: administrador" else "Rol: usuario",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (user.role == Role.ADMIN) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usuarios registrados", fontWeight = FontWeight.SemiBold)
                    users.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(u.username, fontWeight = FontWeight.Medium)
                                Text(
                                    if (u.role == Role.ADMIN) "Administrador" else "Usuario",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (u.id != user.id) {
                                OutlinedButton(onClick = { vm.deleteUser(u) }) { Text("Eliminar") }
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Medidas de seguridad activas", fontWeight = FontWeight.SemiBold)
                Text("• Contraseñas y PIN guardados con hash PBKDF2 + sal", style = MaterialTheme.typography.bodySmall)
                Text("• Bloqueo temporal tras 3 intentos fallidos", style = MaterialTheme.typography.bodySmall)
                Text("• Roles: solo el administrador gestiona usuarios, PIN e historial", style = MaterialTheme.typography.bodySmall)
                Text("• Bluetooth con emparejamiento (enlace cifrado)", style = MaterialTheme.typography.bodySmall)
                Text("• Tráfico HTTP sin cifrar prohibido; sin respaldos de la base de datos", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}
