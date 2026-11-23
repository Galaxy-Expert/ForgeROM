package com.forgerom.app.sources

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure parsing helpers (no Android classes), so they can be unit tested on a normal JVM. */
object SourceParsers {

    /** Reads only the top-level `key: value` lines of the LineageOS wiki YAML. */
    fun parseYaml(text: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (line in text.lineSequence()) {
            if (line.isBlank() || line[0] == ' ' || line[0] == '-' || line[0] == '#') continue
            val i = line.indexOf(':')
            if (i <= 0) continue
            map[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('\'', '"')
        }
        return map
    }

    /** "[a, 'b']" -> ["a", "b"] */
    fun parseList(value: String?): List<String> {
        if (value == null) return emptyList()
        return value.trim().removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotEmpty() && it != "None" }
    }

    fun normalizeModel(m: String): String = m.uppercase().replace("/DS", "").replace(" ", "").trim()

    /** LineageOS version -> Android version (null when unknown). */
    fun lineageToAndroid(branch: String): String? =
        when (branch.substringBefore('.').toIntOrNull()) {
            14 -> "7.1"; 15 -> "8.1"; 16 -> "9"; 17 -> "10"; 18 -> "11"
            19 -> "12"; 20 -> "13"; 21 -> "14"; 22 -> "15"; 23 -> "16"
            else -> null
        }

    // ---- crDroid ----

    class CrDroidInfo(val code: String, val latest: Int, val lastBuild: String?)

    /**
     * crDroid lists every official device on one page with links like .../<codename>/<version>
     * and a "Last build: YYYY-MM-DD" line after them.
     */
    fun parseCrDroid(html: String, code: String): CrDroidInfo? {
        val link = Regex(
            """(?<![A-Za-z0-9_.-])${Regex.escape(code)}/(\d{1,2})(?=["'<\s])""",
            RegexOption.IGNORE_CASE
        )
        val matches = link.findAll(html).toList()
        if (matches.isEmpty()) return null

        val latest = matches.mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: return null
        val best = matches.first { it.groupValues[1].toIntOrNull() == latest }
        val lastEnd = matches.last().range.last
        val date = Regex("""Last build:\s*(?:<[^>]*>\s*)*(\d{4}-\d{2}-\d{2})""").find(html, lastEnd)
            ?.takeIf { it.range.first - lastEnd < 1500 }
            ?.groupValues?.get(1)
        return CrDroidInfo(best.value.substringBefore('/'), latest, date)
    }

    /** True when the last build is older than [days] days (unknown date = not stale). */
    fun isStale(lastBuild: String?, today: LocalDate, days: Long = 365): Boolean {
        if (lastBuild == null) return false
        return try {
            ChronoUnit.DAYS.between(LocalDate.parse(lastBuild), today) > days
        } catch (e: Exception) {
            false
        }
    }

    // ---- LineageOS download API (v1) ----

    class LineageBuild(
        val url: String,
        val fileName: String,
        val sizeBytes: Long?,
        val sha256: String?,
        val version: String?,
        val datetime: Long
    )

    /** Picks the newest build from `{"response":[{url, filename, id (sha256), size, version, datetime}, ...]}`. */
    fun parseLineageBuilds(json: String): LineageBuild? {
        val arr = JSONObject(json).optJSONArray("response") ?: return null
        var best: LineageBuild? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            val name = o.optString("filename")
            if (!url.startsWith("https://") || !name.endsWith(".zip")) continue
            val size = o.optLong("size", -1L)
            val id = o.optString("id")
            val version = o.optString("version")
            val build = LineageBuild(
                url = url,
                fileName = name,
                sizeBytes = if (size > 0) size else null,
                sha256 = if (id.length == 64) id else null,
                version = if (version.isBlank()) null else version,
                datetime = o.optLong("datetime", 0L)
            )
            if (best == null || build.datetime > best.datetime) best = build
        }
        return best
    }

    // ---- App versions ----

    private fun numbers(v: String): List<Int> =
        Regex("""\d+""").findAll(v.substringBefore('-')).map { it.value.toInt() }.take(3).toList()

    fun compareVersions(a: String, b: String): Int {
        val x = numbers(a)
        val y = numbers(b)
        for (i in 0 until 3) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** "v0.5.0-beta" is newer than "0.4.0-alpha". The -alpha/-beta suffix is ignored. */
    fun isNewer(remote: String, current: String): Boolean =
        compareVersions(remote.removePrefix("v"), current.removePrefix("v")) > 0
}
