package com.forgerom.app

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgerom.app.ai.BootloaderInfo
import com.forgerom.app.ai.BootloaderStatus
import com.forgerom.app.ai.DownloadInfo
import com.forgerom.app.ai.RomFinder
import com.forgerom.app.ai.RomSearchResult
import com.forgerom.app.ai.UiText
import com.forgerom.app.ai.UserFacingException
import com.forgerom.app.device.BootloaderPolicy
import com.forgerom.app.device.CatalogEntry
import com.forgerom.app.device.DeviceCatalog
import com.forgerom.app.device.DeviceInfo
import com.forgerom.app.sources.FreeSources
import com.forgerom.app.sources.SourceParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class Screen { ONBOARDING, MAIN, PICKER, SETTINGS, GUIDE }

/** SUGGEST = no root, never changes the device. ROOT = asks for su permission. */
enum class AppMode { SUGGEST, ROOT }

data class UiError(@StringRes val res: Int, val detail: String = "")
data class Notice(@StringRes val res: Int, val positive: Boolean)
data class UpdateInfo(val tag: String, val url: String)

data class UiState(
    val screen: Screen = Screen.MAIN,
    val mode: AppMode = AppMode.SUGGEST,
    val rootGranted: Boolean = false,
    val rootChecking: Boolean = false,
    val notice: Notice? = null,
    val detected: DeviceInfo? = null,
    val detectedName: String? = null,
    val resolvingName: Boolean = true,
    val manual: CatalogEntry? = null,          // manually selected device (replaces the detected one)
    val apiKey: String = "",
    val searching: Boolean = false,
    val aiSearching: Boolean = false,
    val result: RomSearchResult? = null,
    val cachedAt: Long? = null,                // set while the shown result comes from the cache
    val update: UpdateInfo? = null,            // a newer ForgeROM release exists
    val updateCheckEnabled: Boolean = true,
    val crash: String? = null,                 // summary of a crash from the previous run
    val error: UiError? = null,
    val pickerQuery: String = "",
    val pickerResults: List<CatalogEntry> = emptyList(),
    val pickerLoading: Boolean = false
) {
    /** The device used for searching: the manual selection if any, otherwise the detected one. */
    val activeDevice: DeviceInfo?
        get() = manual?.let { m ->
            DeviceInfo(
                manufacturer = m.brand, brand = m.brand, model = m.model, codename = m.codename,
                androidVersion = "", sdk = 0, soc = null
            )
        } ?: detected

    val activeName: String? get() = manual?.name ?: detectedName

    /** "Samsung Galaxy S20 FE SM-G780G (r8q)" or null while the device is still being detected. */
    val displayName: String?
        get() {
            val d = activeDevice ?: return null
            val base = activeName ?: d.manufacturer.replaceFirstChar { it.uppercase() }
            return "$base ${d.model} (${d.codename})".trim()
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("forgerom", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // AI web search can take a while
        .addInterceptor { chain ->
            // OrangeFox asks API clients to send a custom user agent.
            chain.proceed(chain.request().newBuilder().header("User-Agent", "ForgeROM/0.1 (Android)").build())
        }
        .build()
    private val fastClient = client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    private val catalog = DeviceCatalog(app, fastClient)
    private val freeSources = FreeSources(fastClient)
    private val cache = ResultCache(app)
    private val updateChecker = UpdateChecker(fastClient)
    private val appVersion: String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }
    private val finder = RomFinder(client)
    private var pickerJob: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            screen = if (prefs.getBoolean("onboarded", false)) Screen.MAIN else Screen.ONBOARDING,
            apiKey = loadApiKey(),
            updateCheckEnabled = prefs.getBoolean("update_check", true),
            crash = CrashLogger.read(app)
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val device = DeviceInfo.fromBuild()
        _state.update { it.copy(detected = device) }
        loadCached()
        showKnownUpdate()
        checkForUpdate()
        viewModelScope.launch {
            val entry = catalog.resolve(device)
            _state.update { it.copy(detectedName = entry?.name, resolvingName = false) }
        }
        // Returning user who chose root mode: check su again (no prompt if already allowed).
        if (prefs.getString("mode", "suggest") == "root" && prefs.getBoolean("onboarded", false)) {
            applyMode(AppMode.ROOT)
        }
    }

    // ---- Onboarding, mode and settings ----

    fun completeOnboarding(mode: AppMode) {
        applyMode(mode) {
            prefs.edit().putBoolean("onboarded", true).apply()
            _state.update { it.copy(screen = Screen.MAIN) }
        }
    }

    fun setMode(mode: AppMode) = applyMode(mode)
    fun recheckRoot() = applyMode(AppMode.ROOT)

    private fun applyMode(mode: AppMode, onDone: () -> Unit = {}) {
        if (mode == AppMode.SUGGEST) {
            prefs.edit().putString("mode", "suggest").apply()
            _state.update { it.copy(mode = AppMode.SUGGEST, rootGranted = false) }
            onDone()
            return
        }
        _state.update { it.copy(rootChecking = true) }
        viewModelScope.launch {
            val granted = RootManager.requestRoot()
            prefs.edit().putString("mode", if (granted) "root" else "suggest").apply()
            _state.update {
                it.copy(
                    mode = if (granted) AppMode.ROOT else AppMode.SUGGEST,
                    rootGranted = granted,
                    rootChecking = false,
                    notice = Notice(if (granted) R.string.root_granted else R.string.root_denied, granted)
                )
            }
            onDone()
        }
    }

    fun openSettings() = _state.update { it.copy(screen = Screen.SETTINGS) }
    fun closeSettings() = _state.update { it.copy(screen = Screen.MAIN) }
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun setApiKey(key: String) {
        val clean = key.trim()
        saveApiKey(clean)
        _state.update { it.copy(apiKey = clean) }
    }

    // ---- API key storage: encrypted with the Android Keystore ----

    private fun loadApiKey(): String {
        val encrypted = prefs.getString("api_key_enc", null)
        if (encrypted != null) return SecureStore.decrypt(encrypted).orEmpty()

        // Keys saved in plain text by 0.1 / 0.2 are migrated (encrypted, then the plain copy is removed).
        val legacy = prefs.getString("api_key", null)
        if (!legacy.isNullOrBlank()) {
            saveApiKey(legacy)
            return legacy
        }
        return ""
    }

    private fun saveApiKey(key: String) {
        val editor = prefs.edit().remove("api_key")
        try {
            if (key.isBlank()) editor.remove("api_key_enc")
            else editor.putString("api_key_enc", SecureStore.encrypt(key))
        } catch (e: Exception) {
            // Keystore unavailable on this device: keep the key in memory only, never store it in plain text.
            editor.remove("api_key_enc")
        }
        editor.apply()
    }

    fun openGuide() = _state.update { it.copy(screen = Screen.GUIDE) }
    fun closeGuide() = _state.update { it.copy(screen = Screen.MAIN) }

    // ---- Crash report ----

    fun dismissCrash() {
        CrashLogger.clear(getApplication())
        _state.update { it.copy(crash = null) }
    }

    // ---- Update check (GitHub releases, at most once a day) ----

    fun setUpdateCheck(enabled: Boolean) {
        prefs.edit().putBoolean("update_check", enabled).apply()
        _state.update { it.copy(updateCheckEnabled = enabled, update = if (enabled) it.update else null) }
        if (enabled) { showKnownUpdate(); checkForUpdate() }
    }

    fun dismissUpdate() {
        _state.value.update?.let { prefs.edit().putString("dismissed_update", it.tag).apply() }
        _state.update { it.copy(update = null) }
    }

    /** Shows an update found earlier (so the card also appears on launches that skip the daily check). */
    private fun showKnownUpdate() {
        if (!_state.value.updateCheckEnabled) return
        val tag = prefs.getString("update_tag", null) ?: return
        val url = prefs.getString("update_url", null) ?: return
        if (SourceParsers.isNewer(tag, appVersion) && prefs.getString("dismissed_update", null) != tag) {
            _state.update { it.copy(update = UpdateInfo(tag, url)) }
        }
    }

    private fun checkForUpdate() {
        if (!_state.value.updateCheckEnabled) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_update_check", 0L) < 24L * 60 * 60 * 1000) return
        viewModelScope.launch {
            val latest = try {
                withContext(Dispatchers.IO) { updateChecker.latest() }
            } catch (e: Exception) {
                null
            } ?: return@launch
            prefs.edit()
                .putLong("last_update_check", now)
                .putString("update_tag", latest.tag)
                .putString("update_url", latest.url)
                .apply()
            showKnownUpdate()
        }
    }

    // ---- Download (direct file links only; ForgeROM never installs anything) ----

    fun download(file: DownloadInfo) {
        val ok = Downloader.start(getApplication(), file.url, file.fileName)
        _state.update {
            it.copy(notice = Notice(if (ok) R.string.download_started else R.string.download_failed, ok))
        }
    }

    // ---- Manual device selection ----

    fun openPicker() = _state.update { it.copy(screen = Screen.PICKER, pickerQuery = "", pickerResults = emptyList()) }
    fun closePicker() = _state.update { it.copy(screen = Screen.MAIN) }

    fun onPickerQuery(q: String) {
        _state.update { it.copy(pickerQuery = q, pickerLoading = q.isNotBlank()) }
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            delay(150)
            val r = catalog.search(q)
            _state.update { it.copy(pickerResults = r, pickerLoading = false) }
        }
    }

    fun selectManual(entry: CatalogEntry) {
        _state.update { it.copy(manual = entry, screen = Screen.MAIN, result = null, cachedAt = null, error = null) }
        loadCached()
    }

    fun useDetected() {
        _state.update { it.copy(manual = null, result = null, cachedAt = null, error = null) }
        loadCached()
    }

    // ---- Result cache ----

    private fun cacheKey(d: DeviceInfo) = "${d.codename}|${d.model}".lowercase()

    /** Shows saved results (newer than 30 days) for the active device, if there are any. */
    private fun loadCached() {
        val device = _state.value.activeDevice ?: return
        val key = cacheKey(device)
        viewModelScope.launch {
            val hit = withContext(Dispatchers.IO) { cache.get(key) }
            // Ignore the answer if the user switched to another device in the meantime.
            val stillSame = _state.value.activeDevice?.let { cacheKey(it) } == key
            if (stillSame) _state.update { it.copy(result = hit?.result, cachedAt = hit?.savedAt) }
        }
    }

    // ---- Search ----

    /** Free search: LineageOS, crDroid, OrangeFox and TWRP by codename. No API key needed. Always goes online. */
    fun search() {
        val s = _state.value
        val device = s.activeDevice ?: return

        // A local rule (future ready-made database) says locked: nothing to search.
        BootloaderPolicy.check(device)?.let { bl ->
            if (bl.status == BootloaderStatus.LOCKED) {
                _state.update {
                    it.copy(
                        error = null,
                        cachedAt = null,
                        result = RomSearchResult(null, bl, emptyList(), emptyList(), emptyList())
                    )
                }
                return
            }
        }

        // The current (possibly saved) result stays visible while searching.
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            try {
                val free = freeSources.search(device)
                if (!free.reachable) {
                    // Offline: keep whatever is shown (e.g. saved results) and explain.
                    _state.update { it.copy(searching = false, error = UiError(R.string.err_network)) }
                    return@launch
                }
                val bl = free.bootloader ?: BootloaderInfo(BootloaderStatus.UNKNOWN, UiText.res(R.string.bl_unknown_free))
                val locked = bl.status == BootloaderStatus.LOCKED
                val result = RomSearchResult(
                    marketingName = free.marketingName,
                    bootloader = bl,
                    roms = if (locked) emptyList() else free.roms,
                    recoveries = if (locked) emptyList() else free.recoveries,
                    warnings = free.warnings
                )
                if (result.roms.isNotEmpty() || result.recoveries.isNotEmpty() || bl.status != BootloaderStatus.UNKNOWN) {
                    withContext(Dispatchers.IO) { cache.put(cacheKey(device), result, System.currentTimeMillis()) }
                }
                _state.update {
                    it.copy(
                        searching = false,
                        result = result,
                        cachedAt = null,   // fresh results: the "saved results" note disappears
                        detectedName = if (it.manual == null) it.detectedName ?: free.marketingName else it.detectedName
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(searching = false, error = UiError(R.string.err_generic, e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    /**
     * Optional AI deep search (needs the user's own API key). Results are merged into the free results.
     * [language] is the English name of the UI language (e.g. "Turkish"); the AI writes its notes in it.
     */
    fun searchAi(language: String) {
        val s = _state.value
        val device = s.activeDevice ?: return
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(error = UiError(R.string.err_no_api_key)) }
            return
        }
        _state.update { it.copy(aiSearching = true, error = null) }
        viewModelScope.launch {
            try {
                val ai = finder.find(s.apiKey, device, s.activeName, language)
                val current = _state.value
                val merged = merge(current.result, ai)
                // Keep the original save date if the free part came from the cache, so old data is not "refreshed" by accident.
                val savedAt = current.cachedAt ?: System.currentTimeMillis()
                withContext(Dispatchers.IO) { cache.put(cacheKey(device), merged, savedAt) }
                _state.update {
                    it.copy(
                        aiSearching = false,
                        result = merged,
                        detectedName = if (it.manual == null) it.detectedName ?: ai.marketingName else it.detectedName
                    )
                }
            } catch (e: UserFacingException) {
                _state.update { it.copy(aiSearching = false, error = UiError(e.res, e.detail)) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(aiSearching = false, error = UiError(R.string.err_generic, e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    /** Free results first, then AI-only extras. A "locked" verdict from either side wins. */
    private fun merge(free: RomSearchResult?, ai: RomSearchResult): RomSearchResult {
        if (free == null) return ai
        val bl = when {
            free.bootloader.status == BootloaderStatus.LOCKED -> free.bootloader
            ai.bootloader.status == BootloaderStatus.LOCKED -> ai.bootloader
            free.bootloader.status == BootloaderStatus.UNKNOWN -> ai.bootloader
            else -> free.bootloader
        }
        if (bl.status == BootloaderStatus.LOCKED) {
            return RomSearchResult(free.marketingName ?: ai.marketingName, bl, emptyList(), emptyList(), free.warnings)
        }
        val romUrls = free.roms.map { it.url }.toSet()
        val recUrls = free.recoveries.map { it.url }.toSet()
        return RomSearchResult(
            marketingName = free.marketingName ?: ai.marketingName,
            bootloader = bl,
            roms = free.roms + ai.roms.filter { it.url !in romUrls },
            recoveries = free.recoveries + ai.recoveries.filter { it.url !in recUrls },
            warnings = free.warnings + ai.warnings
        )
    }
}
