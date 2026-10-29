package com.example.smartlockmobile.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class Role { ADMIN, USER }

data class User(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val role: Role,
    val failedAttempts: Int,
    val lockedUntil: Long
)

data class EventRecord(
    val id: Long,
    val timestamp: Long,
    val username: String,
    val type: String,
    val detail: String
)

data class EventStats(val opens: Int, val closes: Int, val failures: Int)

object EventType {
    const val LOGIN_OK = "LOGIN_OK"
    const val LOGIN_FAIL = "LOGIN_FAIL"
    const val LOGOUT = "LOGOUT"
    const val REGISTER = "REGISTER"
    const val UNLOCK = "UNLOCK"
    const val LOCK = "LOCK"
    const val PIN_FAIL = "PIN_FAIL"
    const val BLOCKED = "BLOCKED"
    const val LINK_UP = "LINK_UP"
    const val LINK_DOWN = "LINK_DOWN"
    const val ADMIN = "ADMIN"

    fun label(type: String): String = when (type) {
        LOGIN_OK -> "Inicio de sesión"
        LOGIN_FAIL -> "Login fallido"
        LOGOUT -> "Cierre de sesión"
        REGISTER -> "Cuenta creada"
        UNLOCK -> "Cerradura abierta"
        LOCK -> "Cerradura cerrada"
        PIN_FAIL -> "PIN incorrecto"
        BLOCKED -> "Acceso bloqueado"
        LINK_UP -> "Bluetooth conectado"
        LINK_DOWN -> "Bluetooth desconectado"
        ADMIN -> "Acción de administrador"
        else -> type
    }
}

class SmartLockDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "smartlock.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL,
                failed_attempts INTEGER NOT NULL DEFAULT 0,
                locked_until INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                username TEXT NOT NULL,
                type TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL("CREATE INDEX idx_events_user ON events(username)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

fun countUsers(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM users", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun findUser(username: String): User? =
        readableDatabase.rawQuery(
            "SELECT id, username, password_hash, role, failed_attempts, locked_until " +
                "FROM users WHERE username = ?",
            arrayOf(username)
        ).use { if (it.moveToFirst()) it.toUser() else null }

    fun listUsers(): List<User> =
        readableDatabase.rawQuery(
            "SELECT id, username, password_hash, role, failed_attempts, locked_until " +
                "FROM users ORDER BY id",
            null
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.toUser()) }
        }

    fun insertUser(username: String, passwordHash: String, role: Role): Boolean {
        val values = ContentValues().apply {
            put("username", username)
            put("password_hash", passwordHash)
            put("role", role.name)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("users", null, values) != -1L
    }

    fun updateAttempts(id: Long, failedAttempts: Int, lockedUntil: Long) {
        val values = ContentValues().apply {
            put("failed_attempts", failedAttempts)
            put("locked_until", lockedUntil)
        }
        writableDatabase.update("users", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteUser(id: Long) {
        writableDatabase.delete("users", "id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.toUser() = User(
        id = getLong(0),
        username = getString(1),
        passwordHash = getString(2),
        role = runCatching { Role.valueOf(getString(3)) }.getOrDefault(Role.USER),
        failedAttempts = getInt(4),
        lockedUntil = getLong(5)
    )

fun insertEvent(username: String, type: String, detail: String = "") {
        val values = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("username", username)
            put("type", type)
            put("detail", detail.take(120))
        }
        writableDatabase.insert("events", null, values)
    }

    fun listEvents(username: String?, limit: Int = 200): List<EventRecord> {
        val sql = "SELECT id, ts, username, type, detail FROM events " +
            (if (username != null) "WHERE username = ? " else "") +
            "ORDER BY ts DESC, id DESC LIMIT $limit"
        val args = if (username != null) arrayOf(username) else null
        return readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(EventRecord(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4)))
                }
            }
        }
    }

    fun stats(username: String?): EventStats {
        val sql = "SELECT type, COUNT(*) FROM events " +
            "WHERE type IN ('UNLOCK','LOCK','LOGIN_FAIL','PIN_FAIL') " +
            (if (username != null) "AND username = ? " else "") +
            "GROUP BY type"
        val args = if (username != null) arrayOf(username) else null
        var opens = 0
        var closes = 0
        var failures = 0
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    EventType.UNLOCK -> opens += c.getInt(1)
                    EventType.LOCK -> closes += c.getInt(1)
                    else -> failures += c.getInt(1)
                }
            }
        }
        return EventStats(opens, closes, failures)
    }

    fun clearEvents() {
        writableDatabase.delete("events", null, null)
    }
}
