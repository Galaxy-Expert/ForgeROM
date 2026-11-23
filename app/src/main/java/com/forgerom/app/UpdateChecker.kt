package com.forgerom.app

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Looks at the GitHub releases page of ForgeROM. Only a normal web request is made; nothing about the user is sent. */
class UpdateChecker(private val client: OkHttpClient) {

    class Update(val tag: String, val url: String)

    /** Newest published release (pre-releases included), or null. Blocking: call it from a background thread. */
    fun latest(): Update? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/Galaxy-Expert/ForgeROM/releases?per_page=5")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body?.string().orEmpty())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("draft")) continue
                val tag = o.optString("tag_name")
                val url = o.optString("html_url")
                if (tag.isNotBlank() && url.startsWith("https://")) return Update(tag, url)
            }
            return null
        }
    }
}
