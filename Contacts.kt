package com.zenith.assistant

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone

data class Contact(val name: String, val number: String)

object Contacts {
    /** Caller must have READ_CONTACTS. Returns up to 5 distinct matches. */
    fun find(ctx: Context, query: String): List<Contact> {
        val out = LinkedHashMap<String, Contact>()
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            "${Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            while (c.moveToNext() && out.size < 5) {
                val name = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                out.putIfAbsent(num.filter { it.isDigit() || it == '+' }, Contact(name, num))
            }
        }
        return out.values.toList()
    }
}
