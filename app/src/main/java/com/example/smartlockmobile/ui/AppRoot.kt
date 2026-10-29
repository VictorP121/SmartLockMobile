package com.example.smartlockmobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartlockmobile.SmartLockViewModel
import com.example.smartlockmobile.data.User

private enum class Tab(val label: String, val emoji: String) {
    PANEL("Panel", "🔐"),
    CONNECTION("Conexión", "📶"),
    HISTORY("Historial", "📋"),
    ACCOUNT("Cuenta", "👤")
}

@Composable
fun SmartLockApp(vm: SmartLockViewModel = viewModel()) {
    val user by vm.currentUser.collectAsState()
    val current = user
    if (current == null) {
        LoginScreen(vm)
    } else {
        MainScaffold(vm, current)
    }
}

@Composable
private fun MainScaffold(vm: SmartLockViewModel, user: User) {
    var tab by rememberSaveable { mutableStateOf(Tab.PANEL) }
    val snackbar = remember { SnackbarHostState() }
    val info by vm.info.collectAsState()

    LaunchedEffect(info) {
        info?.let {
            snackbar.showSnackbar(it)
            vm.clearInfo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.emoji) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.PANEL -> PanelScreen(vm, user, modifier)
            Tab.CONNECTION -> ConnectionScreen(vm, user, modifier)
            Tab.HISTORY -> HistoryScreen(vm, user, modifier)
            Tab.ACCOUNT -> AccountScreen(vm, user, modifier)
        }
    }
}
