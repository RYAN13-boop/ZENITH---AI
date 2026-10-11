package com.zenith.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Msg(val ts: Long, val role: String, val text: String)

/** Local-only persistent storage (SQLite). Survives app restarts. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "zenith.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL)")
        db.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, text TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addMessage(role: String, text: String) {
        val v = ContentValues().apply {
            put("ts", System.currentTimeMillis()); put("role", role); put("text", text)
        }
        writableDatabase.insert("messages", null, v)
    }

    fun addMemory(text: String) {
        val v = ContentValues().apply { put("ts", System.currentTimeMillis()); put("text", text) }
        writableDatabase.insert("memories", null, v)
    }

    fun memories(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT text FROM memories ORDER BY id", null).use {
            while (it.moveToNext()) out.add(it.getString(0))
        }
        return out
    }

    private fun read(sql: String, args: Array<String>): List<Msg> {
        val out = mutableListOf<Msg>()
        readableDatabase.rawQuery(sql, args).use {
            while (it.moveToNext()) out.add(Msg(it.getLong(0), it.getString(1), it.getString(2)))
        }
        return out
    }

    fun recent(limit: Int): List<Msg> =
        read("SELECT ts,role,text FROM messages ORDER BY id DESC LIMIT $limit", emptyArray()).reversed()

    fun between(from: Long, to: Long): List<Msg> =
        read("SELECT ts,role,text FROM messages WHERE ts>=? AND ts<? ORDER BY id", arrayOf(from.toString(), to.toString()))

    /** Keyword search over what the user said. */
    fun search(q: String, limit: Int = 10): List<Msg> {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.take(5)
        if (words.isEmpty()) return emptyList()
        val where = words.joinToString(" OR ") { "LOWER(text) LIKE ?" }
        return read(
            "SELECT ts,role,text FROM messages WHERE role='user' AND ($where) ORDER BY id DESC LIMIT $limit",
            words.map { "%$it%" }.toTypedArray()
        )
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM messages")
        writableDatabase.execSQL("DELETE FROM memories")
    }
}
