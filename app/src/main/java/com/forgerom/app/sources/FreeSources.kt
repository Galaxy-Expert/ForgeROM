package com.forgerom.app.sources

import com.forgerom.app.R
import com.forgerom.app.ai.BootloaderInfo
import com.forgerom.app.ai.BootloaderStatus
import com.forgerom.app.ai.DownloadInfo
import com.forgerom.app.ai.RecoveryItem
import com.forgerom.app.ai.RomItem
import com.forgerom.app.ai.UiText
import com.forgerom.app.device.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate

data class FreeResult(
    val marketingName: String?,
    val bootloader: BootloaderInfo?,
    val roms: List<RomItem>,
    val recoveries: List<RecoveryItem>,
    val warnings: List<UiText>,
    val reachable: Boolean   // false when no server answered at all (offline)
)

/**
 * Free, key-less sources queried by device codename:
 *  - LineageOS wiki device data (official ROM, supported models, "is_unlockable" flag) + download API (direct file)
 *  - crDroid download list (official ROM)
 *  - OrangeFox public API (official recovery)
 *  - TWRP download page (official recovery)
 * Every source is best-effort: if one fails or the device is unknown there, it is simply skipped.
 */
class FreeSources(private val client: OkHttpClient) {

    @Volatile private var reached = false

    suspend fun search(device: DeviceInfo): FreeResult = coroutineScope {
        val code = device.codename.trim()
        if (!CODENAME.matches(code)) {
            return@coroutineScope FreeResult(null, null, emptyList(), emptyList(), emptyList(), reachable = true)
        }
        reached = false
        val lineage = async(Dispatchers.IO) { runCatching { lineage(code, device.model) }.getOrNull() }
        val crdroid = async(Dispatchers.IO) { runCatching { crDroid(code) }.getOrNull() }
        val fox = async(Dispatchers.IO) { runCatching { orangeFox(code) }.getOrNull() }
        val twrp = async(Dispatchers.IO) { runCatching { twrp(code) }.getOrNull() }

        val l = lineage.await()
        val c = crdroid.await()
        val f = fox.await()
        val t = twrp.await()

        // LineageOS data can say "not unlockable"; otherwise any official project supporting the device is a good sign.
        val bootloader = l?.bootloader
            ?: if (c != null || f != null || t != null) {
                BootloaderInfo(BootloaderStatus.UNLOCKABLE, UiText.res(R.string.bl_projects_ok))
            } else null

        FreeResult(
            marketingName = l?.marketingName,
            bootloader = bootloader,
            roms = listOfNotNull(l?.rom, c),
            recoveries = listOfNotNull(f, t),
            warnings = l?.warnings.orEmpty(),
            reachable = reached
        )
    }

    // ───────── LineageOS ─────────

    private class LineageInfo(
        val marketingName: String?,
        val rom: RomItem,
        val bootloader: BootloaderInfo,
        val warnings: List<UiText>
    )

    private class LineageDoc(val variant: String?, val data: Map<String, String>)

    private fun lineage(code: String, model: String): LineageInfo? {
        val lc = code.lowercase()
        val docs = ArrayList<LineageDoc>()
        getText("$LINEAGE_DATA/$lc.yml")?.let { docs += LineageDoc(null, SourceParsers.parseYaml(it)) }
        if (docs.isEmpty()) {
            // Devices with several hardware variants are stored as <codename>_variantN.yml
            for (i in 1..4) getText("$LINEAGE_DATA/${lc}_variant$i.yml")?.let { docs += LineageDoc("variant$i", SourceParsers.parseYaml(it)) }
        }
        if (docs.isEmpty()) return null

        val wanted = SourceParsers.normalizeModel(model)
        val chosen = docs.firstOrNull { doc ->
            SourceParsers.parseList(doc.data["models"]).any { SourceParsers.normalizeModel(it) == wanted }
        } ?: docs.first()
        val d = chosen.data
        // Variant devices have their own wiki pages: /devices/<codename>/variantN/
        val wikiUrl = "https://wiki.lineageos.org/devices/$lc/" + (chosen.variant?.let { "$it/" } ?: "")

        // Newest build from LineageOS' download API (gives a direct file link, size and SHA-256).
        val build = try {
            getText("https://download.lineageos.org/api/v1/$lc/nightly/forgerom")?.let { SourceParsers.parseLineageBuilds(it) }
        } catch (e: Exception) {
            null
        }

        val title = listOfNotNull(d["vendor"], d["name"]).joinToString(" ").trim()
        val branch = d["current_branch"] ?: build?.version ?: SourceParsers.parseList(d["versions"]).lastOrNull()
        val discontinued = SourceParsers.parseList(d["channels"]).any { it.equals("discontinued", ignoreCase = true) }

        val rom = RomItem(
            name = if (branch != null) "LineageOS $branch" else "LineageOS",
            androidVersion = branch?.let { SourceParsers.lineageToAndroid(it) },
            status = if (discontinued) "abandoned" else "official",
            url = wikiUrl,
            notes = UiText.res(R.string.note_lineage, title.ifBlank { code }),
            download = build?.let { DownloadInfo(it.url, it.fileName, it.sizeBytes, it.sha256) }
        )

        val unlockable = !(d["is_unlockable"]?.equals("false", ignoreCase = true) ?: false)
        val bootloader = if (unlockable) {
            BootloaderInfo(BootloaderStatus.UNLOCKABLE, UiText.res(R.string.bl_lineage_ok))
        } else {
            BootloaderInfo(BootloaderStatus.LOCKED, UiText.res(R.string.bl_lineage_locked))
        }

        val warnings = ArrayList<UiText>()
        val models = SourceParsers.parseList(d["models"])
        if (models.isNotEmpty() && model.isNotBlank() && models.none { SourceParsers.normalizeModel(it) == wanted }) {
            warnings += UiText.res(R.string.warn_model_not_listed, model)
        }
        return LineageInfo(title.ifBlank { null }, rom, bootloader, warnings)
    }

    // ───────── crDroid ─────────

    private fun crDroid(code: String): RomItem? {
        val html = getText("https://crdroid.net/dl.php") ?: return null
        val info = SourceParsers.parseCrDroid(html, code) ?: return null
        val stale = SourceParsers.isStale(info.lastBuild, LocalDate.now())
        return RomItem(
            name = "crDroid ${info.latest}",
            androidVersion = (info.latest + 4).toString(),   // crDroid N is based on Android N+4
            status = if (stale) "abandoned" else "official",
            url = "https://crdroid.net/${info.code}/${info.latest}",
            notes = UiText.res(R.string.note_crdroid, info.lastBuild ?: "?")
        )
    }

    // ───────── OrangeFox ─────────

    private fun orangeFox(code: String): RecoveryItem? {
        val body = getText("https://api.orangefox.download/devices/get?codename=$code") ?: return null
        val o = JSONObject(body)
        val known = o.optString("codename").equals(code, ignoreCase = true) || o.has("_id") || o.has("id")
        if (!known) return null
        val fullName = if (o.isNull("full_name")) code else o.optString("full_name").ifBlank { code }
        val unsupported = o.has("supported") && !o.optBoolean("supported", true)
        return RecoveryItem(
            name = "OrangeFox Recovery",
            url = "https://orangefox.download/device/${code.lowercase()}",
            notes = UiText.res(R.string.note_orangefox, fullName),
            status = if (unsupported) "abandoned" else "official"
        )
    }

    // ───────── TWRP ─────────

    private fun twrp(code: String): RecoveryItem? {
        val url = "https://dl.twrp.me/${code.lowercase()}/"
        val html = getText(url) ?: return null
        if (!html.contains("twrp-", ignoreCase = true)) return null
        return RecoveryItem("TWRP", url, UiText.res(R.string.note_twrp), "official")
    }

    // ───────── helper ─────────

    private fun getText(url: String): String? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            reached = true   // some server answered (even a 404), so we are online
            return if (resp.isSuccessful) resp.body?.string() else null
        }
    }

    private companion object {
        val CODENAME = Regex("^[A-Za-z0-9_+.-]{1,40}$")
        const val LINEAGE_DATA = "https://raw.githubusercontent.com/LineageOS/lineage_wiki/HEAD/_data/devices"
    }
}
