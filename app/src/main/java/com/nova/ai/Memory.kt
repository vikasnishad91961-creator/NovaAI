package com.nova.ai

import android.content.Context

/** User ki pasand / jaankari phone par hi save hoti hai. */
class Memory(ctx: Context) {
    private val p = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)

    var name: String?
        get() = p.getString("name", null)
        set(v) { p.edit().putString("name", v).apply() }

    var city: String?
        get() = p.getString("city", null)
        set(v) { p.edit().putString("city", v).apply() }

    fun notes(): List<String> = (p.getString("notes", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun addNote(n: String) {
        p.edit().putString("notes", (notes() + n).takeLast(30).joinToString("\n")).apply()
    }

    fun summary(): String {
        val parts = mutableListOf<String>()
        name?.let { parts += "Naam: $it" }
        city?.let { parts += "Shehar: $it" }
        notes().forEach { parts += "• $it" }
        return if (parts.isEmpty()) "Abhi mujhe kuch yaad nahi. Bolo: \"yaad rakho ki ...\"" else parts.joinToString("\n")
    }
}
