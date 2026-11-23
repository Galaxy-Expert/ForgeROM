package com.forgerom.app.ai

import com.forgerom.app.device.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.forgerom.app.R

/**
 * Uses the Anthropic Messages API with the web_search tool to find ROMs/recoveries for a device.
 * XDA has no public API, so instead of scraping pages we let the AI research the web.
 */
class RomFinder(private val client: OkHttpClient) {

    suspend fun find(
        apiKey: String,
        device: DeviceInfo,
        marketingName: String?,
        language: String
    ): RomSearchResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 4096)
            .put("system", SYSTEM_PROMPT)
            .put(
                "tools",
                JSONArray().put(
                    JSONObject()
                        .put("type", "web_search_20250305")
                        .put("name", "web_search")
                        .put("max_uses", 6)
                )
            )
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", buildUserPrompt(device, marketingName, language))
                )
            )

        val request = Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw UserFacingException(R.string.err_api, "${resp.code}: ${extractError(raw)}")
            }
            parse(raw)
        }
    }

    private fun buildUserPrompt(d: DeviceInfo, marketingName: String?, language: String): String = buildString {
        appendLine("Device information:")
        appendLine("- Manufacturer: ${d.manufacturer}")
        appendLine("- Marketing name: ${marketingName ?: "unknown, find it on the web"}")
        appendLine("- Model number: ${d.model}")
        appendLine("- Codename: ${d.codename}")
        if (d.sdk > 0) appendLine("- Current Android version: ${d.androidVersion} (API ${d.sdk})")
        d.soc?.let { appendLine("- SoC: $it") }
        appendLine()
        appendLine("Find suitable custom ROMs and custom recoveries for this device.")
        append("Write all text fields in this language: $language.")
    }

    private fun parse(raw: String): RomSearchResult {
        val content = JSONObject(raw).getJSONArray("content")
        val text = buildString {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw UserFacingException(R.string.err_no_result)
        val json = JSONObject(text.substring(start, end + 1))

        val roms = json.optJSONArray("roms").mapObjects { o ->
            val url = o.str("url") ?: ""
            if (!url.isHttpUrl()) null
            else RomItem(
                name = o.str("name") ?: "",
                androidVersion = o.str("android_version"),
                status = o.str("status"),
                url = url,
                notes = o.str("notes")?.let { UiText.raw(it) }
            )
        }
        val recoveries = json.optJSONArray("recoveries").mapObjects { o ->
            val url = o.str("url") ?: ""
            if (!url.isHttpUrl()) null
            else RecoveryItem(
                name = o.str("name") ?: "",
                url = url,
                notes = o.str("notes")?.let { UiText.raw(it) }
            )
        }
        val warnings = buildList {
            val arr = json.optJSONArray("warnings")
            if (arr != null) for (i in 0 until arr.length()) add(UiText.raw(arr.optString(i)))
        }
        val bl = json.optJSONObject("bootloader")
        val blStatus = when (bl?.str("status")?.lowercase()) {
            "unlockable" -> BootloaderStatus.UNLOCKABLE
            "conditional" -> BootloaderStatus.CONDITIONAL
            "locked" -> BootloaderStatus.LOCKED
            else -> BootloaderStatus.UNKNOWN
        }
        val bootloader = BootloaderInfo(blStatus, bl?.str("notes")?.let { UiText.raw(it) })
        val locked = blStatus == BootloaderStatus.LOCKED
        return RomSearchResult(
            marketingName = json.str("marketing_name"),
            bootloader = bootloader,
            roms = if (locked) emptyList() else roms,
            recoveries = if (locked) emptyList() else recoveries,
            warnings = warnings
        )
    }

    private fun extractError(raw: String): String = try {
        JSONObject(raw).getJSONObject("error").optString("message", raw)
    } catch (e: Exception) {
        raw.take(200)
    }

    /** Reads a string field; JSON null or a missing field gives null (plain optString would return "null"). */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifBlank { null }

    private fun String.isHttpUrl() = startsWith("https://") || startsWith("http://")

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>()
        for (i in 0 until length()) optJSONObject(i)?.let { o -> transform(o)?.let(out::add) }
        return out
    }

    companion object {
        // The model and tool versions may change over time; update them here if needed.
        const val MODEL = "claude-sonnet-5-5"
        private const val API_URL = "https://api.anthropic.com/v1/messages"

        private val SYSTEM_PROMPT = """
You are the research assistant of the ForgeROM app. For the given Android device, search the web
(XDA Developers, the LineageOS wiki, TWRP, OrangeFox, PitchBlack, official ROM sites, etc.)
and find custom ROMs and custom recoveries that suit it.

FIRST, research whether the bootloader can be unlocked for this EXACT model/variant/region
(carrier or regional variants, the manufacturer's OEM unlock policy, discontinued unlock programs, etc.).
- "unlockable": it can be unlocked.
- "conditional": it can only be unlocked under certain conditions (carrier, region, waiting period, manufacturer account, etc.).
- "locked": it cannot be unlocked officially or in practice.
- "unknown": you could not find reliable information.
Put a short reason and its source in "notes". If the status is "locked", leave roms and recoveries empty.

Rules:
- Only include results whose page explicitly mentions this device's codename or model number.
- Never invent URLs. Only give URLs you actually saw during your research.
- State whether each item is official, unofficial or abandoned. Avoid old, unmaintained projects.
- If the same marketing name has different hardware variants (e.g. Exynos vs Snapdragon, 4G vs 5G), mention it in "warnings".
- Give at most 5 ROMs and 3 recoveries.
- Put important risks (bootloader unlock, warranty, data loss) briefly into "warnings".
- Write all text fields in the language requested by the user.
- If you are unsure about an item, leave it out. An empty list is better than a wrong recommendation.

Respond with ONLY this JSON object, with no markdown and no extra text:
{
  "marketing_name": "The device's real marketing name, or null",
  "bootloader": {"status": "unlockable|conditional|locked|unknown", "notes": ""},
  "roms": [
    {"name": "", "android_version": "", "status": "official|unofficial|abandoned", "url": "", "notes": ""}
  ],
  "recoveries": [
    {"name": "", "url": "", "notes": ""}
  ],
  "warnings": [""]
}
        """.trimIndent()
    }
}
