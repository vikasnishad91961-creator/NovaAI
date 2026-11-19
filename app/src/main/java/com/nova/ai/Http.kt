package com.nova.ai

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "NEXORA-AI/1.0")
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
