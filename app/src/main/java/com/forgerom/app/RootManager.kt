package com.forgerom.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Asks the root manager (Magisk, KernelSU, APatch...) for superuser permission. */
object RootManager {

    /**
     * Runs `su -c id`. This shows the root manager's permission prompt the first time.
     * Returns true only if the command ran as uid 0.
     */
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            coroutineScope {
                // If the user never answers the prompt, give up after 60 seconds.
                val watchdog = launch { delay(60_000); process.destroy() }
                val output = try {
                    process.inputStream.bufferedReader().readText()
                } catch (e: IOException) {
                    ""
                }
                watchdog.cancel()
                process.destroy()
                output.contains("uid=0")
            }
        } catch (e: Exception) {
            false // su binary not found or could not be started
        }
    }
}
