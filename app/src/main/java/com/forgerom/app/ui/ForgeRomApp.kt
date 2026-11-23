package com.forgerom.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.forgerom.app.AppMode
import com.forgerom.app.R
import com.forgerom.app.ReportBuilder
import com.forgerom.app.Screen
import com.forgerom.app.UiState
import com.forgerom.app.UpdateInfo
import android.text.format.Formatter
import com.forgerom.app.ai.BootloaderInfo
import com.forgerom.app.ai.BootloaderStatus
import com.forgerom.app.ai.DownloadInfo
import com.forgerom.app.ai.RecoveryItem
import com.forgerom.app.ai.RomItem
import com.forgerom.app.ai.UiText
import com.forgerom.app.device.CatalogEntry
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** All user actions in one place, so the screens stay easy to read. */
class Actions(
    val onApiKeyChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onSearchAi: (String) -> Unit,
    val onOpenPicker: () -> Unit,
    val onClosePicker: () -> Unit,
    val onPickerQuery: (String) -> Unit,
    val onSelectManual: (CatalogEntry) -> Unit,
    val onUseDetected: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onCloseSettings: () -> Unit,
    val onSetMode: (AppMode) -> Unit,
    val onCompleteOnboarding: (AppMode) -> Unit,
    val onRecheckRoot: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onSetLanguage: (String) -> Unit,
    val onDownload: (DownloadInfo) -> Unit,
    val onOpenGuide: () -> Unit,
    val onCloseGuide: () -> Unit,
    val onDismissUpdate: () -> Unit,
    val onDismissCrash: () -> Unit,
    val onSetUpdateCheck: (Boolean) -> Unit
)

private const val CONSOLE_URL = "https://console.anthropic.com/settings/keys"

@Composable
fun ForgeRomApp(state: UiState, a: Actions) {
    BackHandler(enabled = state.screen == Screen.PICKER || state.screen == Screen.SETTINGS || state.screen == Screen.GUIDE) {
        when (state.screen) {
            Screen.PICKER -> a.onClosePicker()
            Screen.GUIDE -> a.onCloseGuide()
            else -> a.onCloseSettings()
        }
    }

    AnimatedContent(
        targetState = state.screen,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween<IntOffset>(260)) { it / 14 }) togetherWith
                fadeOut(tween(140))
        },
        label = "screen"
    ) { screen ->
        when (screen) {
            Screen.ONBOARDING -> OnboardingScreen(state.rootChecking, a.onSetLanguage, a.onCompleteOnboarding)
            Screen.MAIN -> MainScreen(state, a)
            Screen.PICKER -> PickerScreen(state, a)
            Screen.SETTINGS -> SettingsScreen(state, a)
            Screen.GUIDE -> GuideScreen(a.onCloseGuide)
        }
    }
}

// ───────────────────────── Main screen ─────────────────────────

@Composable
private fun MainScreen(state: UiState, a: Actions) {
    // English name of the UI language, so the AI writes its notes in the same language.
    val language = LocalConfiguration.current.locales[0].getDisplayLanguage(Locale.ENGLISH)

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Hero(state.mode, a.onOpenSettings) }
            state.update?.let { u ->
                item { Box(Modifier.h()) { UpdateCard(u, a.onDismissUpdate) } }
            }
            state.crash?.let { _ ->
                item { Box(Modifier.h()) { CrashCard(state, a.onDismissCrash) } }
            }
            state.notice?.let { n ->
                item {
                    Box(Modifier.h()) {
                        InfoCard(stringResource(n.res), if (n.positive) InfoStyle.POSITIVE else InfoStyle.ERROR, a.onDismissNotice)
                    }
                }
            }
            item { Box(Modifier.h()) { ModeBanner(state.mode) } }
            item { Box(Modifier.h()) { DeviceCard(state, a.onOpenPicker, a.onUseDetected, a.onSearch) } }
            item {
                Box(Modifier.h()) {
                    Button(
                        onClick = a.onSearch,
                        enabled = !state.searching && state.activeDevice != null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            stringResource(if (state.searching) R.string.search_button_busy else R.string.search_button),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            if (state.searching) {
                item {
                    Box(Modifier.h()) {
                        SearchingCard(listOf(R.string.free_step_1, R.string.free_step_2, R.string.free_step_3))
                    }
                }
            }
            state.error?.let { e ->
                item {
                    Column(Modifier.h(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoCard(stringResource(e.res, e.detail), InfoStyle.ERROR)
                        ReportButton(state)
                    }
                }
            }

            state.result?.let { r ->
                state.cachedAt?.let { ts ->
                    item { Box(Modifier.h()) { CacheNote(ts, state.searching, a.onSearch) } }
                }
                val bl = r.bootloader
                if (bl.status == BootloaderStatus.LOCKED) {
                    item { Box(Modifier.h()) { Appear { LockedCard(bl, a.onOpenPicker) } } }
                } else {
                    item { Box(Modifier.h()) { Appear { BootloaderBadge(bl) } } }
                    r.warnings.forEach { w ->
                        item { Box(Modifier.h()) { InfoCard("⚠ " + w.asString(), InfoStyle.ERROR) } }
                    }
                    if (r.roms.isEmpty() && r.recoveries.isEmpty()) {
                        item { Box(Modifier.h()) { InfoCard(stringResource(R.string.nothing_found), InfoStyle.INFO) } }
                    } else {
                        item { Box(Modifier.h()) { SectionTitle(stringResource(R.string.section_roms)) } }
                        if (r.roms.isEmpty()) item { Box(Modifier.h()) { Text(stringResource(R.string.no_roms)) } }
                        itemsIndexed(r.roms) { i, rom -> Box(Modifier.h()) { Appear(i) { RomCard(rom, a.onDownload) } } }

                        item { Box(Modifier.h()) { SectionTitle(stringResource(R.string.section_recoveries)) } }
                        if (r.recoveries.isEmpty()) item { Box(Modifier.h()) { Text(stringResource(R.string.no_recoveries)) } }
                        itemsIndexed(r.recoveries) { i, rec -> Box(Modifier.h()) { Appear(i) { RecoveryCard(rec) } } }
                    }
                }
                item {
                    Column(Modifier.h(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = a.onOpenGuide, shape = RoundedCornerShape(16.dp)) {
                            Text(stringResource(R.string.guide_button))
                        }
                        ReportButton(state)
                    }
                }
            }

            if (state.aiSearching) {
                item {
                    Box(Modifier.h()) {
                        SearchingCard(listOf(R.string.search_step_1, R.string.search_step_2, R.string.search_step_3, R.string.search_step_4))
                    }
                }
            }
            if (state.result?.bootloader?.status != BootloaderStatus.LOCKED) {
                item { Box(Modifier.h()) { AiCard(state, a.onApiKeyChange) { a.onSearchAi(language) } } }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

@Composable
private fun Hero(mode: AppMode, onSettings: () -> Unit) {
    val settingsLabel = stringResource(R.string.settings_title)
    val modeLabel = stringResource(if (mode == AppMode.ROOT) R.string.badge_mode_root else R.string.badge_mode_suggest)
    GradientHeader {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Logo()
            Column(Modifier.weight(1f).padding(end = 36.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.hero_tagline), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                    Text(
                        "${stringResource(R.string.version_label)} · $modeLabel",
                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
        TextButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().semantics { contentDescription = settingsLabel },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
        ) { Text("⚙", style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
private fun ModeBanner(mode: AppMode) {
    val root = mode == AppMode.ROOT
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (root) R.string.banner_root_title else R.string.banner_suggest_title),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(if (root) R.string.banner_root_text else R.string.banner_suggest_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DeviceCard(state: UiState, onOpenPicker: () -> Unit, onUseDetected: () -> Unit, onSearchAgain: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (state.manual != null) R.string.device_label_manual else R.string.device_label_auto),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.displayName ?: stringResource(R.string.device_detecting),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (state.resolvingName && state.manual == null) {
                    CircularProgressIndicator(Modifier.padding(start = 10.dp).size(16.dp), strokeWidth = 2.dp)
                }
            }
            if (state.manual == null) {
                state.detected?.let { d ->
                    Text(stringResource(R.string.device_android, d.androidVersion, d.sdk), style = MaterialTheme.typography.bodySmall)
                    d.soc?.let { Text(stringResource(R.string.device_soc, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenPicker) { Text(stringResource(R.string.device_change)) }
                if (state.manual != null) TextButton(onClick = onUseDetected) { Text(stringResource(R.string.device_use_detected)) }
                if (state.cachedAt != null) {
                    TextButton(onClick = onSearchAgain, enabled = !state.searching) { Text(stringResource(R.string.search_again)) }
                }
            }
        }
    }
}

@Composable
private fun AiCard(state: UiState, onApiKeyChange: (String) -> Unit, onSearchAi: () -> Unit) {
    val apiKey = state.apiKey
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(apiKey.isBlank()) }
    var guide by remember { mutableStateOf(false) }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.ai_desc), style = MaterialTheme.typography.bodySmall)

            if (expanded || apiKey.isBlank()) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text(stringResource(R.string.api_key_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.api_key_local_note), style = MaterialTheme.typography.bodySmall)
                if (apiKey.isNotBlank()) TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.api_key_done)) }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.api_key_saved), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.api_key_change)) }
                }
            }

            TextButton(onClick = { guide = !guide }) {
                Text((if (guide) "▾ " else "▸ ") + stringResource(R.string.api_guide_toggle))
            }
            if (guide) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.api_guide_step1), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.api_guide_step2), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.api_guide_step3), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.api_guide_step4), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.api_guide_cost), style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = { uriHandler.openUri(CONSOLE_URL) }) {
                        Text(stringResource(R.string.api_guide_open))
                    }
                }
            }

            Button(
                onClick = onSearchAi,
                enabled = apiKey.isNotBlank() && !state.aiSearching && state.activeDevice != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.aiSearching) R.string.ai_button_busy else R.string.ai_button))
            }
        }
    }
}

@Composable
private fun SearchingCard(steps: List<Int>) {
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(3500); step = (step + 1) % steps.size }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha"
    )
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedContent(targetState = step, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "step") { i ->
                Text(
                    stringResource(steps[i]),
                    Modifier.alpha(pulse),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
        }
    }
}

@Composable
private fun UpdateCard(update: UpdateInfo, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.update_available, update.tag),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { uriHandler.openUri(update.url) }) { Text(stringResource(R.string.update_open)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            }
        }
    }
}

@Composable
private fun CrashCard(state: UiState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.crash_title), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.crash_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    uriHandler.openUri(ReportBuilder.issueUrl(context, state))
                    onDismiss()
                }) { Text(stringResource(R.string.crash_report)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.crash_dismiss)) }
            }
        }
    }
}

@Composable
private fun CacheNote(savedAt: Long, busy: Boolean, onSearchAgain: () -> Unit) {
    val date = remember(savedAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(savedAt)) }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.cache_note, date), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onSearchAgain, enabled = !busy) { Text(stringResource(R.string.search_again)) }
        }
    }
}

// ───────────────────────── Result cards ─────────────────────────

@Composable
private fun LockedCard(bl: BootloaderInfo, onPick: () -> Unit) {
    val on = MaterialTheme.colorScheme.onErrorContainer
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.locked_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = on)
            Text(bl.notes?.asString() ?: stringResource(R.string.locked_default_notes), color = on)
            Text(stringResource(R.string.locked_hint), style = MaterialTheme.typography.bodySmall, color = on)
            Button(onClick = onPick, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.locked_button)) }
        }
    }
}

@Composable
private fun BootloaderBadge(bl: BootloaderInfo) {
    val (titleRes, container, content) = when (bl.status) {
        BootloaderStatus.UNLOCKABLE -> Triple(R.string.bootloader_unlockable, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        BootloaderStatus.CONDITIONAL -> Triple(R.string.bootloader_conditional, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        else -> Triple(R.string.bootloader_unknown, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(titleRes), fontWeight = FontWeight.SemiBold, color = content)
            bl.notes?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = content) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StatusChip(status: String) {
    val (color, label) = when (status.lowercase()) {
        "official" -> Color(0xFF1E9E5A) to stringResource(R.string.status_official)
        "unofficial" -> Color(0xFFE08A00) to stringResource(R.string.status_unofficial)
        "abandoned" -> Color(0xFF8A8F98) to stringResource(R.string.status_abandoned)
        else -> Color(0xFF8A8F98) to status
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), contentColor = color) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RomCard(rom: RomItem, onDownload: (DownloadInfo) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rom.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                rom.androidVersion?.let {
                    Text(stringResource(R.string.android_version_fmt, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                rom.status?.let { StatusChip(it) }
            }
            rom.notes?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { uriHandler.openUri(rom.url) }) { Text(stringResource(R.string.open_source)) }
                rom.download?.let { dl ->
                    val label = dl.sizeBytes
                        ?.let { stringResource(R.string.download_size, Formatter.formatShortFileSize(context, it)) }
                        ?: stringResource(R.string.download_button)
                    Button(onClick = { confirm = true }) { Text(label) }
                }
            }
        }
    }

    val dl = rom.download
    if (confirm && dl != null) {
        val size = dl.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "?"
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.download_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.download_confirm_text, dl.fileName, size))
                    dl.sha256?.let { Text(stringResource(R.string.download_checksum, it), style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDownload(dl) }) { Text(stringResource(R.string.download_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun RecoveryCard(rec: RecoveryItem) {
    val uriHandler = LocalUriHandler.current
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rec.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rec.status?.let { StatusChip(it) }
            rec.notes?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium) }
            FilledTonalButton(onClick = { uriHandler.openUri(rec.url) }) { Text(stringResource(R.string.open_source)) }
        }
    }
}

// ───────────────────────── Manual device picker ─────────────────────────

@Composable
private fun PickerScreen(state: UiState, a: Actions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            GradientHeader {
                Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, bottom = 20.dp)) {
                    TextButton(onClick = a.onClosePicker, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.picker_title),
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = state.pickerQuery,
                onValueChange = a.onPickerQuery,
                placeholder = { Text(stringResource(R.string.picker_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus)
            )
            if (state.pickerLoading) LinearProgressIndicator(Modifier.h().fillMaxWidth().clip(CircleShape))

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (state.pickerQuery.isBlank()) {
                    item { Text(stringResource(R.string.picker_start), style = MaterialTheme.typography.bodyMedium) }
                } else if (!state.pickerLoading && state.pickerResults.isEmpty()) {
                    item { Text(stringResource(R.string.picker_empty), style = MaterialTheme.typography.bodyMedium) }
                }
                items(state.pickerResults) { e ->
                    Card(
                        onClick = { a.onSelectManual(e) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${e.model} · ${e.codename}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** Resolves a [UiText] to a string (translated resource or raw AI text). */
@Composable
internal fun UiText.asString(): String {
    val id = res
    return if (id != null) stringResource(id, *args.toTypedArray()) else raw.orEmpty()
}
