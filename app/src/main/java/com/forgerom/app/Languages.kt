package com.forgerom.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

data class Language(val tag: String, val nativeName: String)

/**
 * The language picker is built from the string-array `supported_languages` (res/values/supported_languages.xml).
 * The GitHub workflow regenerates that file from the values-xx folders, so adding a language only needs
 * a new app/strings/values-xx/strings.xml. Display names come from Android itself (in the language's own script).
 */
object Languages {

    fun all(context: Context): List<Language> =
        context.resources.getStringArray(R.array.supported_languages).map { tag ->
            val locale = Locale.forLanguageTag(tag)
            Language(tag, locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) })
        }

    /** Picks the entry that matches the locale currently in use (falls back to English). */
    fun match(tags: List<String>, current: Locale): String =
        tags.firstOrNull { it.equals(current.toLanguageTag(), ignoreCase = true) }
            ?: tags.firstOrNull { tag ->
                val l = Locale.forLanguageTag(tag)
                l.language == current.language && (l.country.isEmpty() || l.country == current.country)
            }
            ?: "en"

    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
