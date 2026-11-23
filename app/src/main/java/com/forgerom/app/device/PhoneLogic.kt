package com.forgerom.app.device

/** Small pure helpers (no Android classes), so they can be unit tested on the JVM. */
object PhoneLogic {

    /** getprop values such as "1" / "0" / "true" / "false" -> Boolean; anything else -> null. */
    fun flag(value: String?): Boolean? = when (value?.trim()?.lowercase()) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }

    /** Is the bootloader locked? Uses the first signal that is available. */
    fun bootloaderLocked(flashLocked: String?, vbmetaState: String?, verifiedBootState: String?): Boolean? {
        flag(flashLocked)?.let { return it }
        when (vbmetaState?.trim()?.lowercase()) {
            "locked" -> return true
            "unlocked" -> return false
        }
        return when (verifiedBootState?.trim()?.lowercase()) {
            "green", "yellow" -> true
            "orange" -> false
            else -> null
        }
    }

    fun percent(level: Int, scale: Int): Int? = if (level >= 0 && scale > 0) level * 100 / scale else null
}
