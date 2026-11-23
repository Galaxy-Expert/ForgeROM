package com.forgerom.app

import android.content.Context
import java.net.URLEncoder
import java.util.Locale

/** Builds a GitHub "new issue" link with the device and result details already filled in. Nothing is sent automatically. */
object ReportBuilder {
    private const val REPO = "https://github.com/Galaxy-Expert/ForgeROM"

    fun issueUrl(context: Context, state: UiState): String {
        val title = "Problem: " + (state.displayName ?: "unknown device")
        val body = body(context, state).take(4000)
        return "$REPO/issues/new?title=${encode(title)}&body=${encode(body)}"
    }

    private fun body(context: Context, state: UiState): String = buildString {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }

        appendLine("<!-- Review this report before sending. Nothing is sent automatically. -->")
        appendLine()
        appendLine("### What happened?")
        appendLine("<!-- Describe the problem here -->")
        appendLine()
        appendLine("### Phone")
        state.detected?.let { d ->
            appendLine("- Model: ${d.model}")
            appendLine("- Codename: ${d.codename}")
            appendLine("- Android: ${d.androidVersion} (API ${d.sdk})")
            d.soc?.let { appendLine("- SoC: $it") }
        }
        appendLine()
        appendLine("### Searched device")
        appendLine("- ${state.displayName ?: "unknown"}")
        appendLine("- Selected manually: ${if (state.manual != null) "yes" else "no"}")
        appendLine()
        appendLine("### App")
        appendLine("- ForgeROM: ${version ?: "unknown"}")
        appendLine("- Mode: ${state.mode}")
        appendLine("- Language: ${Locale.getDefault().toLanguageTag()}")

        state.result?.let { r ->
            appendLine()
            appendLine("### Results")
            appendLine("- Bootloader: ${r.bootloader.status}")
            appendLine("- ROMs: " + r.roms.joinToString { "${it.name} (${it.status ?: "?"})" }.ifBlank { "none" })
            appendLine("- Recoveries: " + r.recoveries.joinToString { it.name }.ifBlank { "none" })
            appendLine("- Shown from saved results: ${if (state.cachedAt != null) "yes" else "no"}")
        }
        state.crash?.let { crash ->
            appendLine()
            appendLine("### Crash (previous run)")
            appendLine("```")
            appendLine(crash.take(1500))
            appendLine("```")
        }
        state.error?.let { e ->
            appendLine()
            appendLine("### Error")
            val key = try { context.resources.getResourceEntryName(e.res) } catch (x: Exception) { "?" }
            appendLine("`$key` ${e.detail.take(300)}")
        }
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
