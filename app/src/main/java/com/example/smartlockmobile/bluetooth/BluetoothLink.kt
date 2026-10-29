package com.example.smartlockmobile.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object LockProtocol {
    const val CMD_LOCK = "LOCK"
    const val CMD_UNLOCK_PREFIX = "UNLOCK:"
    const val CMD_STATUS = "STATUS?"

    const val ST_LOCKED = "STATUS:LOCKED"
    const val ST_UNLOCKED = "STATUS:UNLOCKED"
    const val ST_BLOCKED = "STATUS:BLOCKED"
    const val ERR_INVALID_PIN = "ERROR:INVALID_PIN"
    const val ERR_PIN_NOT_SET = "ERROR:PIN_NOT_SET"
    const val ERR_UNKNOWN = "ERROR:UNKNOWN_COMMAND"
}

data class PairedDevice(val name: String, val address: String)

sealed interface LinkState {
    object Idle : LinkState
    object Listening : LinkState
    class Connecting(val name: String) : LinkState
    class Connected(val name: String) : LinkState
    class Failed(val reason: String) : LinkState
}

class BluetoothLink(context: Context) {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val MAX_LINE = 128
    }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = AtomicInteger(0)

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var worker: Job? = null
    @Volatile private var output: OutputStream? = null

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> {
        if (adapter == null || !hasPermission()) return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.name ?: "Sin nombre", it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

@SuppressLint("MissingPermission")
    fun startListening() {
        val id = beginSession()
        val bt = adapter
        if (bt == null || !hasPermission() || !bt.isEnabled) {
            _state.value = LinkState.Failed("Bluetooth no disponible o sin permiso")
            return
        }
        _state.value = LinkState.Listening
        worker = scope.launch {
            try {
                val server = bt.listenUsingRfcommWithServiceRecord("SmartLock", SPP_UUID)
                serverSocket = server
                val client = server.accept() // bloquea hasta que otro teléfono se conecte
                runCatching { server.close() }
                serverSocket = null
                runConnection(client, id)
            } catch (e: IOException) {
                fail(id, "No se pudo recibir la conexión")
            } catch (e: SecurityException) {
                fail(id, "Falta el permiso de Bluetooth")
            }
        }
    }

@SuppressLint("MissingPermission")
    fun connectTo(address: String) {
        val id = beginSession()
        val bt = adapter
        if (bt == null || !hasPermission() || !bt.isEnabled) {
            _state.value = LinkState.Failed("Bluetooth no disponible o sin permiso")
            return
        }
        val device = try {
            bt.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            _state.value = LinkState.Failed("Dirección Bluetooth inválida")
            return
        }
        val name = try { device.name ?: address } catch (e: SecurityException) { address }
        _state.value = LinkState.Connecting(name)
        worker = scope.launch {
            try {
                try { bt.cancelDiscovery() } catch (e: SecurityException) { }
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                runConnection(s, id)
            } catch (e: IOException) {
                fail(id, "No se pudo conectar. ¿La otra app está esperando conexión?")
            } catch (e: SecurityException) {
                fail(id, "Falta el permiso de Bluetooth")
            }
        }
    }

fun send(line: String) {
        val out = output ?: return
        scope.launch {
            try {
                synchronized(this@BluetoothLink) {
                    out.write((line + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            } catch (e: IOException) {
            }
        }
    }

    fun disconnect() {
        beginSession()
        _state.value = LinkState.Idle
    }

    fun release() {
        disconnect()
    }

    private fun beginSession(): Int {
        val id = session.incrementAndGet()
        output = null
        runCatching { serverSocket?.close() }
        runCatching { socket?.close() }
        serverSocket = null
        socket = null
        worker?.cancel()
        worker = null
        return id
    }

    private suspend fun runConnection(s: BluetoothSocket, id: Int) {
        if (id != session.get()) {
            runCatching { s.close() }
            return
        }
        socket = s
        output = s.outputStream
        _state.value = LinkState.Connected(remoteName(s))
        try {
            val input = BufferedInputStream(s.inputStream)
            while (true) {
                val line = readLine(input) ?: break
                if (line.isNotEmpty()) _incoming.emit(line)
            }
        } catch (e: IOException) {
            // conexión cerrada o mensaje inválido
        } finally {
            runCatching { s.close() }
            if (id == session.get()) {
                output = null
                socket = null
                _state.value = LinkState.Failed("Conexión cerrada")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun remoteName(s: BluetoothSocket): String =
        try { s.remoteDevice.name ?: s.remoteDevice.address } catch (e: SecurityException) { "dispositivo" }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            if (b == '\n'.code) return sb.toString().trim()
            if (sb.length >= MAX_LINE) throw IOException("Mensaje demasiado largo")
            sb.append(b.toChar())
        }
    }

    private fun fail(id: Int, reason: String) {
        if (id == session.get()) _state.value = LinkState.Failed(reason)
    }
}
