package com.forgerom.app.device

import com.forgerom.app.ai.BootloaderInfo

/**
 * Place for local (ready-made database) bootloader rules.
 *
 * Empty for now: the unlock status comes from the AI research.
 * Later, parameterized rules can be added here, for example:
 *   - Samsung + Android 16 or newer -> BootloaderStatus.LOCKED
 * Returning null means "no local rule, let the AI research decide".
 * If LOCKED is returned, no search is made at all (so no API cost either).
 */
object BootloaderPolicy {
    fun check(device: DeviceInfo): BootloaderInfo? = null
}
