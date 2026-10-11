package com.zenith.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Msg(val id: Long = 0, val ts: Long, val role: String, val text: String)
data class MemoryItem(val id: Long = 0, val ts: Long, val text: String, val category: String = "general")

/**
 * Local-only persistent storage using SQLite.
 * Survives app restarts. Stores chats, long-term memory items, and app settings.
 */
class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "zenith.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE messages(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER NOT NULL, " +
                "role TEXT NOT NULL, " +
                "text TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE memories(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER NOT NULL, " +
                "text TEXT NOT NULL, " +
                "category TEXT DEFAULT 'general')"
        )
        db.execSQL(
            "CREATE TABLE settings(" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            try {
                db.execSQL("ALTER TABLE memories ADD COLUMN category TEXT DEFAULT 'general'")
            } catch (_: Exception) {}
        }
    }

    // ---------- Settings ----------
    fun setSetting(key: String, value: String) {
        val v = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSetting(key: String, defaultVal: String): String {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return defaultVal
    }

    fun getBooleanSetting(key: String, defaultVal: Boolean): Boolean {
        return getSetting(key, if (defaultVal) "1" else "0") == "1"
    }

    fun setBooleanSetting(key: String, value: Boolean) {
        setSetting(key, if (value) "1" else "0")
    }

    // ---------- Messages ----------
    fun addMessage(role: String, text: String) {
        val v = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("role", role)
            put("text", text)
        }
        writableDatabase.insert("messages", null, v)
    }

    private fun readMessages(sql: String, args: Array<String>): List<Msg> {
        val out = mutableListOf<Msg>()
        readableDatabase.rawQuery(sql, args).use {
            while (it.moveToNext()) {
                out.add(Msg(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3)))
            }
        }
        return out
    }

    fun recent(limit: Int): List<Msg> =
        readMessages("SELECT id,ts,role,text FROM messages ORDER BY id DESC LIMIT $limit", emptyArray()).reversed()

    fun between(from: Long, to: Long): List<Msg> =
        readMessages("SELECT id,ts,role,text FROM messages WHERE ts>=? AND ts<? ORDER BY id", arrayOf(from.toString(), to.toString()))

    fun search(q: String, limit: Int = 10): List<Msg> {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.take(5)
        if (words.isEmpty()) return emptyList()
        val where = words.joinToString(" OR ") { "LOWER(text) LIKE ?" }
        return readMessages(
            "SELECT id,ts,role,text FROM messages WHERE role='user' AND ($where) ORDER BY id DESC LIMIT $limit",
            words.map { "%$it%" }.toTypedArray()
        )
    }

    // ---------- Long-Term Memories ----------
    fun addMemory(text: String, category: String = "general"): Long {
        if (!getBooleanSetting("memory_enabled", true)) return -1
        val v = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("text", text)
            put("category", category)
        }
        return writableDatabase.insert("memories", null, v)
    }

    fun memories(): List<MemoryItem> {
        val out = mutableListOf<MemoryItem>()
        readableDatabase.rawQuery("SELECT id,ts,text,category FROM memories ORDER BY id DESC", null).use {
            while (it.moveToNext()) {
                out.add(MemoryItem(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3)))
            }
        }
        return out
    }

    fun memoryStrings(): List<String> = memories().map { it.text }

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memories", "id=?", arrayOf(id.toString()))
    }

    fun deleteMemoryByKeyword(keyword: String): Int {
        return writableDatabase.delete("memories", "LOWER(text) LIKE ?", arrayOf("%${keyword.lowercase()}%"))
    }

    fun searchMemories(query: String): List<MemoryItem> {
        val q = "%${query.lowercase()}%"
        val out = mutableListOf<MemoryItem>()
        readableDatabase.rawQuery("SELECT id,ts,text,category FROM memories WHERE LOWER(text) LIKE ? ORDER BY id DESC", arrayOf(q)).use {
            while (it.moveToNext()) {
                out.add(MemoryItem(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3)))
            }
        }
        return out
    }

    // ---------- Data Export & Deletion ----------
    fun exportJson(): String {
        val root = JSONObject()

        val msgsArr = JSONArray()
        recent(1000).forEach { m ->
            msgsArr.put(JSONObject().apply {
                put("ts", m.ts)
                put("role", m.role)
                put("text", m.text)
            })
        }
        root.put("messages", msgsArr)

        val memsArr = JSONArray()
        memories().forEach { m ->
            memsArr.put(JSONObject().apply {
                put("id", m.id)
                put("ts", m.ts)
                put("text", m.text)
                put("category", m.category)
            })
        }
        root.put("memories", memsArr)

        return root.toString(2)
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM messages")
        writableDatabase.execSQL("DELETE FROM memories")
    }

    fun clearMessages() {
        writableDatabase.execSQL("DELETE FROM messages")
    }

    fun clearMemories() {
        writableDatabase.execSQL("DELETE FROM memories")
    }
}
