package com.forgerom.app.device

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** One row of Google's Play device list: SM-G780G / r8q -> Samsung Galaxy S20 FE */
data class CatalogEntry(
    val brand: String,
    val marketing: String,
    val codename: String,
    val model: String
) {
    val name: String
        get() {
            val b = brand.replaceFirstChar { it.uppercase() }
            return if (marketing.startsWith(b, ignoreCase = true)) marketing else "$b $marketing".trim()
        }
    internal val haystack: String = "$brand $marketing $model $codename".lowercase()
}

/**
 * Downloads Google's public "supported devices" list (cached for 30 days).
 * Used both to resolve a model number to its real name (SM-G780G -> Samsung Galaxy S20 FE)
 * and to power the manual device picker.
 */
class DeviceCatalog(
    private val context: Context,
    private val client: OkHttpClient
) {
    @Volatile private var cache: List<CatalogEntry>? = null
    private val mutex = Mutex()
    private val cacheFile get() = File(context.filesDir, "supported_devices.csv")

    suspend fun entries(): List<CatalogEntry> = cache ?: mutex.withLock {
        cache ?: withContext(Dispatchers.IO) { load() }.also { if (it.isNotEmpty()) cache = it }
    }

    /** Finds the best entry for the build info: model + codename first, then model, then codename. */
    suspend fun resolve(info: DeviceInfo): CatalogEntry? = withContext(Dispatchers.Default) {
        var byModel: CatalogEntry? = null
        var byCodename: CatalogEntry? = null
        for (e in entries()) {
            val m = e.model.equals(info.model, ignoreCase = true)
            val d = e.codename.equals(info.codename, ignoreCase = true)
            if (m && d) return@withContext e
            if (m && byModel == null) byModel = e
            if (d && byCodename == null) byCodename = e
        }
        byModel ?: byCodename
    }

    /** Manual picker search: every typed word must appear in the name, model or codename. */
    suspend fun search(query: String, limit: Int = 40): List<CatalogEntry> =
        withContext(Dispatchers.Default) {
            val tokens = query.lowercase().split(' ').filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@withContext emptyList()
            entries().asSequence()
                .filter { e -> tokens.all { e.haystack.contains(it) } }
                .take(limit)
                .toList()
        }

    private fun load(): List<CatalogEntry> {
        val csv: String? = try { loadCsv() } catch (e: Exception) { null }
        if (csv == null) return emptyList()
        return csv.lineSequence().drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val c = parseLine(line)
            if (c.size < 4 || c[1].isBlank()) null
            else CatalogEntry(c[0].trim(), c[1].trim(), c[2].trim(), c[3].trim())
        }.distinctBy { "${it.name}|${it.model}|${it.codename}" }.toList()
    }

    private fun loadCsv(): String? {
        val f = cacheFile
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS
        if (fresh) return f.readText()
        val downloaded = try { download() } catch (e: Exception) { null }
        if (downloaded != null) {
            f.writeText(downloaded)
            return downloaded
        }
        return if (f.exists()) f.readText() else null
    }

    private fun download(): String? {
        val request = Request.Builder().url(CSV_URL).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            return decode(bytes)
        }
    }

    /** Google publishes this file as UTF-16LE (with BOM); UTF-8 is supported as a fallback. */
    private fun decode(bytes: ByteArray): String {
        val utf16le = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        val text = if (utf16le) String(bytes, Charsets.UTF_16LE) else String(bytes, Charsets.UTF_8)
        return text.removePrefix("\uFEFF")
    }

    /** Small CSV line parser that supports quoted fields. */
    private fun parseLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private companion object {
        const val CSV_URL = "https://storage.googleapis.com/play_public/supported_devices.csv"
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
