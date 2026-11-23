package com.forgerom.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgerom.app.AppMode
import com.forgerom.app.R
import com.forgerom.app.UiState

/** First-launch screen: language + mode (suggestion / root). */
@Composable
internal fun OnboardingScreen(
    checking: Boolean,
    onSetLanguage: (String) -> Unit,
    onContinue: (AppMode) -> Unit
) {
    var selected by remember { mutableStateOf(AppMode.SUGGEST) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GradientHeader {
                Column(
                    Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Logo(72)
                    Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.onb_subtitle), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                }
            }
            Column(Modifier.h().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LanguageSection(onSetLanguage)
                ModeSection(selected, enabled = !checking) { selected = it }
                Text(stringResource(R.string.onb_warning), style = MaterialTheme.typography.bodySmall)
                if (checking) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                Button(
                    onClick = { onContinue(selected) },
                    enabled = !checking,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        stringResource(if (checking) R.string.onb_root_requesting else R.string.onb_continue),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}

/** Settings: change language and mode later. */
@Composable
internal fun SettingsScreen(state: UiState, a: Actions) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GradientHeader {
                Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, bottom = 20.dp)) {
                    TextButton(onClick = a.onCloseSettings, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.h().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LanguageSection(a.onSetLanguage)
                ModeSection(state.mode, enabled = !state.rootChecking, onSelect = a.onSetMode)
                if (state.rootChecking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                    Text(stringResource(R.string.onb_root_requesting), style = MaterialTheme.typography.bodySmall)
                }
                if (state.mode == AppMode.ROOT) {
                    if (state.rootGranted) Text(stringResource(R.string.root_granted), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = a.onRecheckRoot, enabled = !state.rootChecking, shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(R.string.root_recheck))
                    }
                }
                state.notice?.let { n ->
                    InfoCard(stringResource(n.res), if (n.positive) InfoStyle.POSITIVE else InfoStyle.ERROR, onDismiss = a.onDismissNotice)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_updates_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.updateCheckEnabled, onCheckedChange = a.onSetUpdateCheck)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportButton(state)
                    Text(stringResource(R.string.report_note), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}
