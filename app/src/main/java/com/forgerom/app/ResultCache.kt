package com.forgerom.app

import android.content.Context
import com.forgerom.app.ai.BootloaderInfo
import com.forgerom.app.ai.BootloaderStatus
import com.forgerom.app.ai.DownloadInfo
import com.forgerom.app.ai.RecoveryItem
import com.forgerom.app.ai.RomItem
import com.forgerom.app.ai.RomSearchResult
import com.forgerom.app.ai.UiText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves search results per device in a small JSON file, so they can be shown again without searching.
 * Results older than 30 days are ignored. Translatable texts are stored by resource NAME, because
 * resource numbers change between app versions.
 */
class ResultCache(private val context: Context) {

    class Hit(val result: RomSearchResult, val savedAt: Long)

    private val file get() = File(context.filesDir, "result_cache_v2.json")

    fun get(key: String): Hit? {
        return try {
            val entry = readAll().optJSONObject(key) ?: return null
            val savedAt = entry.getLong("savedAt")
            if (System.currentTimeMillis() - savedAt > MAX_AGE_MS) return null
            Hit(resultFromJson(entry.getJSONObject("result")), savedAt)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun put(key: String, result: RomSearchResult, savedAt: Long) {
        try {
            val all = readAll()
            all.put(key, JSONObject().put("savedAt", savedAt).put("result", resultToJson(result)))

            // Drop expired entries, then keep only the newest MAX_ENTRIES.
            val now = System.currentTimeMillis()
            val entries = all.keys().asSequence().toList()
                .map { k -> k to all.optJSONObject(k)?.optLong("savedAt", 0L)!! }
            entries.filter { now - it.second > MAX_AGE_MS }.forEach { all.remove(it.first) }
            entries.filter { now - it.second <= MAX_AGE_MS }
                .sortedByDescending { it.second }
                .drop(MAX_ENTRIES)
                .forEach { all.remove(it.first) }

            file.writeText(all.toString())
        } catch (e: Exception) {
            // The cache is only a convenience; ignore any problem.
        }
    }

    private fun readAll(): JSONObject {
        if (!file.exists()) return JSONObject()
        return try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
    }

    // ---- JSON conversion ----

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun UiText.toJson(): JSONObject {
        val id = res
        return if (id != null) {
            JSONObject()
                .put("res", context.resources.getResourceEntryName(id))
                .put("args", JSONArray(args))
        } else {
            JSONObject().put("raw", raw.orEmpty())
        }
    }

    private fun uiTextFromJson(o: JSONObject?): UiText? {
        if (o == null) return null
        if (o.has("res")) {
            val id = context.resources.getIdentifier(o.getString("res"), "string", context.packageName)
            if (id == 0) return null
            val a = o.optJSONArray("args")
            val args = if (a == null) emptyList<String>() else List(a.length()) { a.optString(it) }
            return UiText.res(id, *args.toTypedArray())
        }
        return UiText.raw(o.optString("raw"))
    }

    private fun resultToJson(r: RomSearchResult): JSONObject = JSONObject()
        .put("marketingName", r.marketingName ?: JSONObject.NULL)
        .put("blStatus", r.bootloader.status.name)
        .put("blNotes", r.bootloader.notes?.toJson() ?: JSONObject.NULL)
        .put("roms", JSONArray(r.roms.map { rom ->
            JSONObject()
                .put("name", rom.name)
                .put("android", rom.androidVersion ?: JSONObject.NULL)
                .put("status", rom.status ?: JSONObject.NULL)
                .put("url", rom.url)
                .put("notes", rom.notes?.toJson() ?: JSONObject.NULL)
                .put("dl", rom.download?.let { dl ->
                    JSONObject()
                        .put("url", dl.url)
                        .put("file", dl.fileName)
                        .put("size", dl.sizeBytes ?: JSONObject.NULL)
                        .put("sha", dl.sha256 ?: JSONObject.NULL)
                } ?: JSONObject.NULL)
        }))
        .put("recoveries", JSONArray(r.recoveries.map { rec ->
            JSONObject()
                .put("name", rec.name)
                .put("url", rec.url)
                .put("status", rec.status ?: JSONObject.NULL)
                .put("notes", rec.notes?.toJson() ?: JSONObject.NULL)
        }))
        .put("warnings", JSONArray(r.warnings.map { it.toJson() }))

    private fun resultFromJson(o: JSONObject): RomSearchResult {
        val roms = o.getJSONArray("roms").let { arr ->
            List(arr.length()) { i ->
                val x = arr.getJSONObject(i)
                RomItem(
                    name = x.getString("name"),
                    androidVersion = x.str("android"),
                    status = x.str("status"),
                    url = x.getString("url"),
                    notes = uiTextFromJson(x.optJSONObject("notes")),
                    download = x.optJSONObject("dl")?.let { dl ->
                        DownloadInfo(
                            url = dl.getString("url"),
                            fileName = dl.getString("file"),
                            sizeBytes = if (dl.isNull("size")) null else dl.optLong("size"),
                            sha256 = dl.str("sha")
                        )
                    }
                )
            }
        }
        val recoveries = o.getJSONArray("recoveries").let { arr ->
            List(arr.length()) { i ->
                val x = arr.getJSONObject(i)
                RecoveryItem(
                    name = x.getString("name"),
                    url = x.getString("url"),
                    notes = uiTextFromJson(x.optJSONObject("notes")),
                    status = x.str("status")
                )
            }
        }
        val warnings = o.getJSONArray("warnings").let { arr ->
            List(arr.length()) { i -> uiTextFromJson(arr.optJSONObject(i)) }.filterNotNull()
        }
        return RomSearchResult(
            marketingName = o.str("marketingName"),
            bootloader = BootloaderInfo(
                BootloaderStatus.valueOf(o.getString("blStatus")),
                uiTextFromJson(o.optJSONObject("blNotes"))
            ),
            roms = roms,
            recoveries = recoveries,
            warnings = warnings
        )
    }

    private companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000   // 30 days
        const val MAX_ENTRIES = 30
    }
}
