package com.example.smartlockmobile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlockmobile.bluetooth.BluetoothLink
import com.example.smartlockmobile.bluetooth.LinkState
import com.example.smartlockmobile.bluetooth.LockProtocol
import com.example.smartlockmobile.data.EventRecord
import com.example.smartlockmobile.data.EventStats
import com.example.smartlockmobile.data.EventType
import com.example.smartlockmobile.data.Role
import com.example.smartlockmobile.data.SmartLockDb
import com.example.smartlockmobile.data.User
import com.example.smartlockmobile.security.InputValidator
import com.example.smartlockmobile.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class LockState { UNKNOWN, CLOSED, OPEN }

enum class DeviceRole { LOCK, CONTROLLER }

class SmartLockViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val MAX_ATTEMPTS = 3          // mismo criterio que MAX_INTENTOS del Arduino
        const val BLOCK_MS = 30_000L
    }

    private val db = SmartLockDb(app)
    private val prefs = app.getSharedPreferences("smartlock_prefs", Context.MODE_PRIVATE)
    val link = BluetoothLink(app)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy

    private val _deviceRole = MutableStateFlow(DeviceRole.LOCK)
    val deviceRole: StateFlow<DeviceRole> = _deviceRole

    private val _lockState = MutableStateFlow(LockState.CLOSED)
    val lockState: StateFlow<LockState> = _lockState

    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked

    private val _events = MutableStateFlow<List<EventRecord>>(emptyList())
    val events: StateFlow<List<EventRecord>> = _events

    private val _stats = MutableStateFlow(EventStats(0, 0, 0))
    val stats: StateFlow<EventStats> = _stats

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _pinConfigured = MutableStateFlow(prefs.contains("pin_hash"))
    val pinConfigured: StateFlow<Boolean> = _pinConfigured

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    private var pinFails = 0
    private var blockJob: Job? = null

    init {
        // Mensajes que llegan por Bluetooth
        viewModelScope.launch(Dispatchers.Default) {
            link.incoming.collect { handleIncoming(it) }
        }
        // Cambios en la conexión
        viewModelScope.launch(Dispatchers.Default) {
            var previous: LinkState = LinkState.Idle
            link.state.collect { s ->
                val wasConnected = previous is LinkState.Connected
                if (s is LinkState.Connected && !wasConnected) {
                    logEvent(EventType.LINK_UP, s.name)
                    // Sincroniza el estado apenas se conectan
                    if (_deviceRole.value == DeviceRole.LOCK) link.send(statusLine())
                    else link.send(LockProtocol.CMD_STATUS)
                } else if (wasConnected && s !is LinkState.Connected) {
                    if (_currentUser.value != null) logEvent(EventType.LINK_DOWN)
                    if (_deviceRole.value == DeviceRole.CONTROLLER) _lockState.value = LockState.UNKNOWN
                }
                previous = s
            }
        }
    }

fun login(usernameInput: String, password: String) {
        val username = usernameInput.trim()
        viewModelScope.launch(Dispatchers.IO) {
            _authBusy.value = true
            _authMessage.value = null
            val user = db.findUser(username)
            val now = System.currentTimeMillis()
            when {
                user == null -> {
                    _authMessage.value = "Usuario o contraseña incorrectos."
                    db.insertEvent(username.take(20).ifBlank { "?" }, EventType.LOGIN_FAIL, "usuario inexistente")
                }
                user.lockedUntil > now -> {
                    val secs = (user.lockedUntil - now) / 1000 + 1
                    _authMessage.value = "Cuenta bloqueada temporalmente. Intenta en $secs s."
                }
                PasswordHasher.verify(password, user.passwordHash) -> {
                    db.updateAttempts(user.id, 0, 0)
                    _currentUser.value = user
                    db.insertEvent(user.username, EventType.LOGIN_OK)
                    refreshData()
                }
                else -> {
                    val attempts = user.failedAttempts + 1
                    if (attempts >= MAX_ATTEMPTS) {
                        db.updateAttempts(user.id, 0, now + BLOCK_MS)
                        _authMessage.value = "Demasiados intentos. Cuenta bloqueada por ${BLOCK_MS / 1000} s."
                    } else {
                        db.updateAttempts(user.id, attempts, 0)
                        _authMessage.value =
                            "Usuario o contraseña incorrectos. Intentos restantes: ${MAX_ATTEMPTS - attempts}."
                    }
                    db.insertEvent(user.username, EventType.LOGIN_FAIL, "intento $attempts")
                }
            }
            _authBusy.value = false
        }
    }

    fun register(usernameInput: String, password: String, confirm: String) {
        val username = usernameInput.trim()
        val error = InputValidator.usernameError(username)
            ?: InputValidator.passwordError(password)
            ?: if (password != confirm) "Las contraseñas no coinciden." else null
        if (error != null) {
            _authMessage.value = error
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _authBusy.value = true
            _authMessage.value = null
            // El primer usuario registrado es ADMIN; los demás son USER
            val role = if (db.countUsers() == 0) Role.ADMIN else Role.USER
            val ok = db.insertUser(username, PasswordHasher.hash(password), role)
            if (!ok) {
                _authMessage.value = "Ese nombre de usuario ya existe."
            } else {
                db.insertEvent(username, EventType.REGISTER, role.name)
                val user = db.findUser(username)
                _currentUser.value = user
                refreshData()
            }
            _authBusy.value = false
        }
    }

    fun logout() {
        val name = _currentUser.value?.username ?: return
        link.disconnect()
        viewModelScope.launch(Dispatchers.IO) {
            db.insertEvent(name, EventType.LOGOUT)
            _currentUser.value = null
            _events.value = emptyList()
            _users.value = emptyList()
            _authMessage.value = null
        }
    }

    fun clearAuthMessage() { _authMessage.value = null }

private fun isAdmin() = _currentUser.value?.role == Role.ADMIN

    fun deleteUser(target: User) {
        if (!isAdmin() || target.id == _currentUser.value?.id) return
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteUser(target.id)
            logEvent(EventType.ADMIN, "eliminó a ${target.username}")
        }
    }

    fun clearHistory() {
        if (!isAdmin()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.clearEvents()
            logEvent(EventType.ADMIN, "borró el historial")
        }
    }

    fun savePin(pin: String) {
        if (!isAdmin()) { _info.value = "Solo el administrador puede cambiar el PIN."; return }
        if (!InputValidator.isValidPin(pin)) { _info.value = "El PIN debe tener de 4 a 8 dígitos."; return }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString("pin_hash", PasswordHasher.hash(pin)).apply()
            _pinConfigured.value = true
            pinFails = 0
            _info.value = "PIN guardado."
            logEvent(EventType.ADMIN, "cambió el PIN de la cerradura")
        }
    }

fun setDeviceRole(role: DeviceRole) {
        if (role == _deviceRole.value) return
        link.disconnect()
        _deviceRole.value = role
        _lockState.value = if (role == DeviceRole.LOCK) LockState.CLOSED else LockState.UNKNOWN
        _blocked.value = false
        blockJob?.cancel()
    }

    fun toggleLocal() {
        if (_deviceRole.value != DeviceRole.LOCK) return
        applyLock(if (_lockState.value == LockState.OPEN) LockState.CLOSED else LockState.OPEN, "pulsador")
    }

    private fun applyLock(new: LockState, source: String) {
        val changed = _lockState.value != new
        _lockState.value = new
        if (changed) logEvent(if (new == LockState.OPEN) EventType.UNLOCK else EventType.LOCK, source)
        link.send(statusLine())
    }

    private fun statusLine() =
        if (_lockState.value == LockState.OPEN) LockProtocol.ST_UNLOCKED else LockProtocol.ST_LOCKED

    // --- Comandos del teléfono-control ---
    fun remoteUnlock(pin: String) {
        if (!InputValidator.isValidPin(pin)) { _info.value = "El PIN debe tener de 4 a 8 dígitos."; return }
        link.send(LockProtocol.CMD_UNLOCK_PREFIX + pin)
    }

    fun remoteLock() = link.send(LockProtocol.CMD_LOCK)

    fun remoteRefresh() = link.send(LockProtocol.CMD_STATUS)

    // --- Mensajes recibidos ---
    private fun handleIncoming(line: String) {
        if (_currentUser.value == null) return // sin sesión no se procesan comandos
        if (_deviceRole.value == DeviceRole.LOCK) handleCommand(line) else handleStatus(line)
    }

    private fun handleCommand(line: String) {
        when {
            line == LockProtocol.CMD_STATUS -> link.send(statusLine())
            line == LockProtocol.CMD_LOCK -> applyLock(LockState.CLOSED, "remoto")
            line.startsWith(LockProtocol.CMD_UNLOCK_PREFIX) -> {
                if (_blocked.value) {
                    link.send(LockProtocol.ST_BLOCKED)
                    return
                }
                val storedHash = prefs.getString("pin_hash", null)
                if (storedHash == null) {
                    link.send(LockProtocol.ERR_PIN_NOT_SET)
                    return
                }
                val pin = line.removePrefix(LockProtocol.CMD_UNLOCK_PREFIX)
                if (PasswordHasher.verify(pin, storedHash)) {
                    pinFails = 0
                    applyLock(LockState.OPEN, "remoto")
                } else {
                    pinFails++
                    logEvent(EventType.PIN_FAIL, "intento $pinFails")
                    if (pinFails >= MAX_ATTEMPTS) {
                        startBlock()
                        logEvent(EventType.BLOCKED, "${BLOCK_MS / 1000} s")
                        link.send(LockProtocol.ST_BLOCKED)
                    } else {
                        link.send(LockProtocol.ERR_INVALID_PIN)
                    }
                }
            }
            else -> link.send(LockProtocol.ERR_UNKNOWN)
        }
    }

    private fun handleStatus(line: String) {
        when (line) {
            LockProtocol.ST_LOCKED -> setRemoteState(LockState.CLOSED)
            LockProtocol.ST_UNLOCKED -> setRemoteState(LockState.OPEN)
            LockProtocol.ST_BLOCKED -> {
                startBlock()
                logEvent(EventType.BLOCKED, "la cerradura bloqueó el acceso")
                _info.value = "Cerradura bloqueada por intentos fallidos."
            }
            LockProtocol.ERR_INVALID_PIN -> {
                logEvent(EventType.PIN_FAIL, "PIN rechazado por la cerradura")
                _info.value = "PIN incorrecto."
            }
            LockProtocol.ERR_PIN_NOT_SET -> _info.value = "La cerradura aún no tiene un PIN configurado."
            else -> Unit
        }
    }

    private fun setRemoteState(new: LockState) {
        if (_lockState.value != new) {
            logEvent(if (new == LockState.OPEN) EventType.UNLOCK else EventType.LOCK, "remoto")
        }
        _lockState.value = new
    }

    private fun startBlock() {
        _blocked.value = true
        blockJob?.cancel()
        blockJob = viewModelScope.launch {
            delay(BLOCK_MS)
            _blocked.value = false
            pinFails = 0
        }
    }

fun startListening() = link.startListening()
    fun connectTo(address: String) = link.connectTo(address)
    fun disconnectLink() = link.disconnect()

private fun logEvent(type: String, detail: String = "") {
        val name = _currentUser.value?.username ?: "sistema"
        viewModelScope.launch(Dispatchers.IO) {
            db.insertEvent(name, type, detail)
            refreshData()
        }
    }

    private fun refreshData() {
        val user = _currentUser.value ?: return
        val filter = if (user.role == Role.ADMIN) null else user.username
        _events.value = db.listEvents(filter)
        _stats.value = db.stats(filter)
        _users.value = if (user.role == Role.ADMIN) db.listUsers() else emptyList()
    }

    fun clearInfo() { _info.value = null }

    override fun onCleared() {
        link.release()
        db.close()
        super.onCleared()
    }
}
