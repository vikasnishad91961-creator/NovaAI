package com.nova.ai

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Assistant(private val ctx: Context) {
    private val mem = Memory(ctx)
    private val llm = LocalLlm(ctx)

    suspend fun handle(raw: String, imageCtx: String? = null): String = withContext(Dispatchers.IO) {
        try { route(raw.trim(), imageCtx) } catch (e: Exception) { "Kuch gadbad ho gayi: ${e.message}" }
    }

    // ---------- helpers ----------
    private fun has(t: String, vararg k: String) = k.any { t.contains(it) }
    private fun strip(t: String, stop: Set<String>) =
        t.split(Regex("\\s+")).filter { it.isNotBlank() && it !in stop }.joinToString(" ")
    private fun go(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }
    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun hindi(t: String) = t.any { it in '\u0900'..'\u097F' }

    private val common = setOf("nova", "please", "plz", "ko", "ka", "ki", "ke", "me", "mein", "में", "का", "की", "को", "के",
        "karo", "kar", "kro", "do", "de", "करो", "कर", "दो", "दे", "hai", "है", "the", "a", "on", "par", "पर", "to", "for", "ek", "एक")

    // ---------- router ----------
    private fun route(raw: String, imageCtx: String?): String {
        val t = raw.lowercase()
        if (imageCtx != null) return askLlm(raw, imageCtx)

        // 1. Yaaddasht
        if (has(t, "mera naam kya", "what is my name", "मेरा नाम क्या")) return mem.name?.let { "Aapka naam $it hai." } ?: "Mujhe aapka naam nahi pata."
        Regex("(?:mera naam|my name is|मेरा नाम)\\s+(.+?)(?:\\s+(?:hai|है))?$").find(t)?.let {
            mem.name = it.groupValues[1].trim().replaceFirstChar { c -> c.uppercase() }
            return "Theek hai ${mem.name}, yaad rakh liya."
        }
        Regex("(?:mera shehar|mera sheher|my city is|मेरा शहर)\\s+(.+?)(?:\\s+(?:hai|है))?$").find(t)?.let {
            mem.city = it.groupValues[1].trim().replaceFirstChar { c -> c.uppercase() }
            return "Shehar ${mem.city} yaad rakh liya."
        }
        Regex("(?:yaad rakho|remember|याद रखो)(?:\\s+(?:ki|that|कि))?\\s+(.+)").find(t)?.let {
            mem.addNote(it.groupValues[1]); return "Yaad rakh liya."
        }
        if (has(t, "kya yaad hai", "what do you remember", "क्या याद है")) return mem.summary()

        // 2. Timer / Reminder / Alarm
        val dur = Regex("(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|ghante|ghanta|घंटे|मिनट|सेकंड)").find(t)
        val isRem = has(t, "remind", "yaad dila", "याद दिला", "रिमाइंडर")
        if (dur != null && (isRem || has(t, "timer", "टाइमर", "baad", "बाद"))) {
            val n = dur.groupValues[1].toInt()
            val u = dur.groupValues[2]
            val secs = n * when {
                u.startsWith("h") || u.startsWith("g") || u.startsWith("घ") -> 3600
                u.startsWith("m") || u.startsWith("म") -> 60
                else -> 1
            }
            go(Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Nova")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "Theek hai, $n ${u} ka timer laga diya."
        }
        if (has(t, "alarm", "अलार्म", "jagao", "जगा") || isRem) {
            val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(t) ?: return "Kitne baje ka alarm lagana hai?"
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            val pm = has(t, "pm", "shaam", "sham", "raat", "dopahar", "शाम", "रात", "दोपहर")
            if (pm && h < 12) h += 12
            if (has(t, " am") && h == 12) h = 0
            if (h > 23 || min > 59) return "Samay samajh nahi aaya, dobara bolo."
            go(Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Nova")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "Alarm ${"%02d:%02d".format(h, min)} ke liye set ho gaya."
        }

        // 3. Mausam
        if (has(t, "mausam", "weather", "मौसम", "temperature", "tapman")) {
            val city = strip(t, common + setOf("mausam", "weather", "मौसम", "aaj", "आज", "kaisa", "कैसा", "today", "in", "of",
                "temperature", "tapman", "kya", "क्या", "batao", "बताओ", "what", "is", "how"))
                .ifEmpty { mem.city ?: "" }
            if (city.isEmpty()) return "Kaunse shehar ka mausam? Aap bol sakte ho: \"mera shehar Delhi hai\"."
            return weather(city)
        }

        // 4. Call
        if (Regex("\\bcall\\b").containsMatchIn(t) || has(t, "कॉल", "फोन", "phone laga", "phone kar")) {
            val target = strip(t, common + setOf("call", "कॉल", "फोन", "phone", "laga", "lagao", "लगाओ", "karna", "करना"))
            val digits = target.filter { it.isDigit() || it == '+' }
            val number = if (digits.count { it.isDigit() } >= 5) digits else findNumber(target)
            return if (number == null) "\"$target\" ka number nahi mila." else {
                val action = if (granted(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
                go(Intent(action, Uri.parse("tel:$number")))
                "$target ko call kar raha hoon."
            }
        }

        // 5. Message (SMS composer khulta hai, aap 'Send' dabate ho)
        if (has(t, "message", "sms", "msg", "मैसेज")) {
            val r1 = Regex("(?:send )?(?:message|sms|msg) (?:to )?(\\S+) (.+)").find(t)
            val r2 = Regex("(\\S+) (?:ko|को) (?:message|sms|msg|मैसेज) (?:bhejo|send karo|karo|भेजो|करो)(?: (?:ki|कि))? ?(.*)").find(t)
            val m = r2 ?: r1
            if (m != null) {
                val who = m.groupValues[1]
                val body = m.groupValues[2]
                val number = if (who.count { it.isDigit() } >= 5) who else findNumber(who)
                if (number == null) return "\"$who\" ka number nahi mila."
                go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", body))
                return "$who ke liye message tayyar hai, bas Send dabaiye."
            }
            return "Aise bolo: \"Rahul ko message bhejo ki main late hoon\"."
        }

        // 6. App kholna
        if (has(t, "open ", "kholo", "khol do", "khol de", "खोलो", "खोल", "launch ")) {
            var name = strip(t, common + setOf("open", "kholo", "khol", "खोलो", "खोल", "app", "ऐप", "एप", "launch", "chalu", "चालू"))
            val alias = mapOf("यूट्यूब" to "youtube", "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "कैमरा" to "camera",
                "सेटिंग" to "settings", "गूगल" to "google", "क्रोम" to "chrome", "गैलरी" to "gallery", "कैलकुलेटर" to "calculator")
            alias.forEach { (k, v) -> if (name.contains(k)) name = v }
            return openApp(name)
        }

        // 7. Music / Video
        if (has(t, "play ", "chalao", "bajao", "gaana", "song", "चलाओ", "बजाओ", "गाना", "गाने")) {
            val video = has(t, "video", "youtube", "वीडियो", "यूट्यूब")
            val q = strip(t, common + setOf("play", "chalao", "bajao", "gaana", "gana", "song", "songs", "चलाओ", "बजाओ", "गाना", "गाने",
                "video", "youtube", "वीडियो", "यूट्यूब", "music", "सुनाओ", "sunao"))
            if (video) {
                go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Http.enc(q)}")))
                return "YouTube par \"$q\" khol raha hoon."
            }
            return try {
                go(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    .putExtra(SearchManager.QUERY, q))
                "\"$q\" chala raha hoon."
            } catch (e: ActivityNotFoundException) {
                go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Http.enc(q)}")))
                "Music app nahi mila, YouTube par khol raha hoon."
            }
        }

        // 8. Math
        mathAnswer(t)?.let { return it }

        // 9. Samay / Tareekh
        if (has(t, "kitne baje", "samay", "समय", "what time", "time kya", "current time", "टाइम")) {
            return "Abhi ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())} baje hain."
        }
        if (has(t, "tareekh", "tarikh", "तारीख", "date", "aaj kya din", "कौन सा दिन", "which day", "what day")) {
            return "Aaj ${SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())} hai."
        }

        // 10. Internet se jaankari
        if (has(t, "search", "dhundo", "khojo", "ढूंढो", "खोजो", "wikipedia", "internet")) {
            val q = strip(t, common + setOf("search", "dhundo", "khojo", "ढूंढो", "खोजो", "google", "wikipedia", "internet", "about", "batao", "बताओ", "for"))
            return wiki(q)
        }

        // 11. Baaki sab: offline AI dimaag
        return askLlm(raw, null)
    }

    // ---------- skills ----------
    private fun findNumber(name: String): String? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null
        )?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun openApp(name: String): String {
        if (name.isBlank()) return "Kaunsa app kholna hai?"
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(main, 0).firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
            ?: return "\"$name\" naam ka app nahi mila."
        val i = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return "App khul nahi paya."
        go(i)
        return "${match.loadLabel(pm)} khol raha hoon."
    }

    private fun mathAnswer(t: String): String? {
        var s = t
        val words = listOf("divided by" to "/", "multiplied by" to "*", "plus" to "+", "jod" to "+", "minus" to "-", "ghata" to "-",
            "times" to "*", "into" to "*", "gunaa" to "*", "guna" to "*", "bhaag" to "/", "bhag" to "/", "power" to "^",
            "×" to "*", "÷" to "/", "x" to "*", "प्लस" to "+", "माइनस" to "-", "गुणा" to "*", "भाग" to "/")
        val cues = listOf("calculate", "solve", "what is", "what's", "kitna hota hai", "kitna hai", "kya hota hai", "kya hai", "hisab", "=", "?", "बराबर", "कितना")
        cues.forEach { s = s.replace(it, " ") }
        words.forEach { (w, sym) -> s = s.replace(w, sym) }
        s = s.trim()
        if (!Regex("^[0-9+\\-*/^().%\\s]+$").matches(s)) return null
        if (!Regex("[0-9]\\s*[+\\-*/^]\\s*[0-9(]").containsMatchIn(s)) return null
        return try { "Jawab hai ${Calc.fmt(Calc(s.replace("%", "/100")).eval())}." } catch (e: Exception) { null }
    }

    private fun weather(city: String): String {
        val g = JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=${Http.enc(city)}&count=1"))
        val p = g.optJSONArray("results")?.getJSONObject(0) ?: return "\"$city\" shehar nahi mila."
        val w = JSONObject(Http.get("https://api.open-meteo.com/v1/forecast?latitude=${p.getDouble("latitude")}" +
            "&longitude=${p.getDouble("longitude")}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"))
            .getJSONObject("current")
        val code = w.getInt("weather_code")
        val desc = when (code) {
            0 -> "saaf aasman"; 1, 2, 3 -> "halke baadal"; 45, 48 -> "kohra"
            in 51..57 -> "halki phuhaar"; in 61..67 -> "baarish"; in 71..77 -> "barfbaari"
            in 80..82 -> "tez baarish"; in 95..99 -> "aandhi-toofan"; else -> "mausam mila-jula"
        }
        return "${p.getString("name")} mein abhi ${w.getDouble("temperature_2m").toInt()}°C hai, $desc. " +
            "Nami ${w.getInt("relative_humidity_2m")}%, hawa ${w.getDouble("wind_speed_10m").toInt()} km/h."
    }

    private fun wiki(q: String): String {
        if (q.isBlank()) return "Kya dhundna hai?"
        val lang = if (hindi(q)) "hi" else "en"
        val j = JSONObject(Http.get("https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${Http.enc(q)}" +
            "&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&format=json"))
        val pages = j.optJSONObject("query")?.optJSONObject("pages") ?: return "\"$q\" ke baare mein kuch nahi mila."
        val extract = pages.getJSONObject(pages.keys().next()).optString("extract")
        if (extract.isBlank()) return "\"$q\" ke baare mein kuch nahi mila."
        return extract.split(Regex("(?<=[.।!?])\\s+")).take(3).joinToString(" ").take(500)
    }

    private fun askLlm(question: String, imageCtx: String?): String {
        if (!llm.available()) {
            if (imageCtx != null) return imageCtx
            return try { wiki(question) } catch (e: Exception) {
                "Offline AI model abhi install nahi hai (README dekho), aur internet bhi nahi mila."
            }
        }
        val known = mem.summary().takeIf { mem.name != null || mem.city != null || mem.notes().isNotEmpty() } ?: ""
        val prompt = buildString {
            append("You are Nova, a fast, friendly voice assistant. Reply briefly (1-3 sentences) in the same language the user used (Hindi, Hinglish or English). ")
            append("If asked for study help, explain step by step but simply.\n")
            if (known.isNotEmpty()) append("About the user:\n$known\n")
            if (imageCtx != null) append("The user just took a photo. $imageCtx\n")
            append("User: $question\nNova:")
        }
        return llm.ask(prompt).ifEmpty { "Samajh nahi aaya, dobara bolo." }
    }
}
