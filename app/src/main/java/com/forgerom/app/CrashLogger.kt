package com.forgerom.app

import android.content.Context
import java.io.File

/** Saves a short crash summary to a file; the next launch offers to report it. Nothing leaves the device by itself. */
object CrashLogger {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = buildString {
                    appendLine(error.toString())
                    error.stackTrace.take(12).forEach { appendLine("    at $it") }
                    error.cause?.let { appendLine("Caused by: $it") }
                }
                File(app.filesDir, FILE).writeText(text.take(2000))
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) f.readText().ifBlank { null } else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
