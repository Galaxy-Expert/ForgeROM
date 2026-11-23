package com.forgerom.app.device

import android.os.Build

/** Raw device info read from the build properties. */
data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,      // e.g. SM-G780G
    val codename: String,   // e.g. r8q (the key used when searching for ROMs/recoveries)
    val androidVersion: String,
    val sdk: Int,
    val soc: String?
) {
    companion object {
        fun fromBuild(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            codename = Build.DEVICE.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            soc = if (Build.VERSION.SDK_INT >= 31) {
                listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                    .filter { it.isNotBlank() && it != Build.UNKNOWN }
                    .joinToString(" ")
                    .ifBlank { null }
            } else null
        )
    }
}
