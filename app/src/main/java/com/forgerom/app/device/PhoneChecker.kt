package com.forgerom.app.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import androidx.annotation.StringRes
import com.forgerom.app.R
import com.forgerom.app.ai.UiText
import java.util.concurrent.TimeUnit

enum class CheckStatus { OK, WARN, INFO, UNKNOWN }

data class PhoneCheck(
    @StringRes val title: Int,
    val status: CheckStatus,
    val value: UiText?,
    val hint: UiText? = null
)

data class PhoneInfo(
    val checks: List<PhoneCheck>,
    val technical: Map<String, String>   // label/property -> value, in English, for copying and reports
) {
    val reportText: String get() = technical.entries.joinToString("\n") { "${it.key}: ${it.value}" }
}

/** Reads a few settings from this phone to see whether it is ready for a custom ROM. It never changes anything. */
object PhoneChecker {
    private const val MIN_BATTERY = 80
    private const val MIN_FREE_BYTES = 8L * 1024 * 1024 * 1024

    fun collect(context: Context): PhoneInfo {
        val checks = ArrayList<PhoneCheck>()
        val tech = LinkedHashMap<String, String>()
        tech["Model"] = Build.MODEL.orEmpty()
        tech["Device"] = Build.DEVICE.orEmpty()
        tech["Android"] = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        /** Reads a system property and records it in the technical list. */
        fun p(key: String): String? {
            val v = prop(key)
            tech[key] = v ?: "n/a"
            return v
        }
        fun yesNo(b: Boolean?) = UiText.res(
            when (b) {
                true -> R.string.value_yes
                false -> R.string.value_no
                null -> R.string.value_unknown
            }
        )
        fun onOff(b: Boolean?) = UiText.res(
            when (b) {
                true -> R.string.value_on
                false -> R.string.value_off
                null -> R.string.value_unknown
            }
        )

        // ---- Battery ----
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = battery?.let {
            PhoneLogic.percent(it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), it.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
        }
        val charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        if (percent == null) {
            checks += PhoneCheck(R.string.check_battery, CheckStatus.UNKNOWN, UiText.res(R.string.value_unknown))
        } else {
            val value = if (charging) UiText.res(R.string.value_charging, "$percent%") else UiText.raw("$percent%")
            checks += if (percent >= MIN_BATTERY) {
                PhoneCheck(R.string.check_battery, CheckStatus.OK, value)
            } else {
                PhoneCheck(R.string.check_battery, CheckStatus.WARN, value, UiText.res(R.string.hint_battery))
            }
        }
        tech["Battery"] = if (percent == null) "n/a" else "$percent%" + if (charging) " (charging)" else ""

        // ---- Free storage ----
        val free = try { StatFs(Environment.getDataDirectory().path).availableBytes } catch (e: Exception) { null }
        if (free == null) {
            checks += PhoneCheck(R.string.check_storage, CheckStatus.UNKNOWN, UiText.res(R.string.value_unknown))
        } else {
            val value = UiText.raw(Formatter.formatShortFileSize(context, free))
            checks += if (free >= MIN_FREE_BYTES) {
                PhoneCheck(R.string.check_storage, CheckStatus.OK, value)
            } else {
                PhoneCheck(R.string.check_storage, CheckStatus.WARN, value, UiText.res(R.string.hint_storage))
            }
        }
        tech["Free storage"] = free?.let { "${it / (1024 * 1024)} MB" } ?: "n/a"

        // ---- Developer options and USB debugging ----
        fun setting(name: String): Boolean? =
            try { Settings.Global.getInt(context.contentResolver, name) != 0 } catch (e: Exception) { null }

        val devOptions = setting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        checks += when (devOptions) {
            true -> PhoneCheck(R.string.check_devopts, CheckStatus.OK, onOff(true))
            false -> PhoneCheck(R.string.check_devopts, CheckStatus.WARN, onOff(false), UiText.res(R.string.hint_devopts))
            null -> PhoneCheck(R.string.check_devopts, CheckStatus.UNKNOWN, onOff(null))
        }
        val adb = setting(Settings.Global.ADB_ENABLED)
        checks += when (adb) {
            true -> PhoneCheck(R.string.check_usbdebug, CheckStatus.OK, onOff(true))
            false -> PhoneCheck(R.string.check_usbdebug, CheckStatus.WARN, onOff(false), UiText.res(R.string.hint_usbdebug))
            null -> PhoneCheck(R.string.check_usbdebug, CheckStatus.UNKNOWN, onOff(null))
        }
        tech["Developer options"] = devOptions?.toString() ?: "n/a"
        tech["USB debugging"] = adb?.toString() ?: "n/a"

        // ---- OEM unlocking ----
        val oemSupported = PhoneLogic.flag(p("ro.oem_unlock_supported"))
        val oemAllowed = PhoneLogic.flag(p("sys.oem_unlock_allowed"))
        checks += when {
            oemSupported == false -> PhoneCheck(
                R.string.check_oemunlock, CheckStatus.WARN,
                UiText.res(R.string.value_not_supported), UiText.res(R.string.hint_oem_unsupported)
            )
            oemAllowed == true -> PhoneCheck(R.string.check_oemunlock, CheckStatus.OK, onOff(true))
            oemAllowed == false -> PhoneCheck(
                R.string.check_oemunlock, CheckStatus.WARN, onOff(false), UiText.res(R.string.hint_oem_off)
            )
            else -> PhoneCheck(R.string.check_oemunlock, CheckStatus.UNKNOWN, UiText.res(R.string.value_unknown))
        }

        // ---- Bootloader state (what the phone reports right now) ----
        val locked = PhoneLogic.bootloaderLocked(
            p("ro.boot.flash.locked"), p("ro.boot.vbmeta.device_state"), p("ro.boot.verifiedbootstate")
        )
        checks += when (locked) {
            true -> PhoneCheck(
                R.string.check_bootloader, CheckStatus.INFO,
                UiText.res(R.string.value_locked), UiText.res(R.string.hint_bootloader_locked)
            )
            false -> PhoneCheck(R.string.check_bootloader, CheckStatus.OK, UiText.res(R.string.value_unlocked))
            null -> PhoneCheck(R.string.check_bootloader, CheckStatus.UNKNOWN, UiText.res(R.string.value_unknown))
        }

        // ---- Partition layout ----
        fun infoFlag(title: Int, b: Boolean?) =
            PhoneCheck(title, if (b == null) CheckStatus.UNKNOWN else CheckStatus.INFO, yesNo(b))
        checks += infoFlag(R.string.check_ab, PhoneLogic.flag(p("ro.build.ab_update")))
        checks += infoFlag(R.string.check_treble, PhoneLogic.flag(p("ro.treble.enabled")))
        checks += infoFlag(R.string.check_dynamic, PhoneLogic.flag(p("ro.boot.dynamic_partitions")))

        // ---- CPU, security patch ----
        val abis = Build.SUPPORTED_ABIS.toList()
        checks += PhoneCheck(R.string.check_abi, CheckStatus.INFO, UiText.raw(abis.firstOrNull() ?: "?"))
        tech["ABI"] = abis.joinToString()
        val patch = Build.VERSION.SECURITY_PATCH
        checks += PhoneCheck(R.string.check_patch, CheckStatus.INFO, UiText.raw(patch ?: "?"))
        tech["Security patch"] = patch ?: "n/a"

        // ---- Connection ----
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = if (cm != null && network != null) cm.getNetworkCapabilities(network) else null
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        checks += when {
            onWifi -> PhoneCheck(R.string.check_connection, CheckStatus.OK, UiText.res(R.string.value_wifi))
            caps != null -> PhoneCheck(
                R.string.check_connection, CheckStatus.WARN, UiText.res(R.string.value_mobile), UiText.res(R.string.hint_connection)
            )
            else -> PhoneCheck(
                R.string.check_connection, CheckStatus.WARN, UiText.res(R.string.value_offline), UiText.res(R.string.hint_connection)
            )
        }
        tech["Connection"] = if (onWifi) "wifi" else if (caps != null) "mobile" else "offline"

        return PhoneInfo(checks, tech)
    }

    private fun prop(key: String): String? = try {
        val process = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(2, TimeUnit.SECONDS)
        out.ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
