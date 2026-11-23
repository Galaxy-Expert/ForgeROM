package com.forgerom.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.forgerom.app.AppMode
import com.forgerom.app.Languages
import com.forgerom.app.R
import com.forgerom.app.ReportBuilder
import com.forgerom.app.UiState
import kotlinx.coroutines.delay

internal val CardShape = RoundedCornerShape(24.dp)
internal val HeroBrush = Brush.linearGradient(listOf(Color(0xFF0066FF), Color(0xFF0040B8)))

internal fun Modifier.h() = padding(horizontal = 16.dp)

/** Blue gradient header with rounded bottom corners (used on every screen). */
@Composable
internal fun GradientHeader(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(HeroBrush),
        content = content
    )
}

/** Two-layer logo: background + foreground (the PNGs in drawable-xxxhdpi). */
@Composable
internal fun Logo(size: Int = 64) {
    val shape = RoundedCornerShape((size * 0.31f).dp)
    Box(Modifier.size(size.dp).border(1.5.dp, Color.White.copy(alpha = 0.35f), shape).clip(shape)) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Fades the content in with a small delay, so lists appear smoothly one after another. */
@Composable
internal fun Appear(index: Int = 0, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 70L); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) + slideInVertically(tween<IntOffset>(320)) { it / 6 }
    ) { content() }
}

enum class InfoStyle { ERROR, INFO, POSITIVE }

@Composable
internal fun InfoCard(text: String, style: InfoStyle = InfoStyle.ERROR, onDismiss: (() -> Unit)? = null) {
    val container = when (style) {
        InfoStyle.ERROR -> MaterialTheme.colorScheme.errorContainer
        InfoStyle.INFO -> MaterialTheme.colorScheme.surfaceVariant
        InfoStyle.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
    }
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguageSection(onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val languages = remember { Languages.all(context) }
    val effective = Languages.match(languages.map { it.tag }, LocalConfiguration.current.locales[0])
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { lang ->
                FilterChip(
                    selected = lang.tag == effective,
                    onClick = { onSelect(lang.tag) },
                    label = { Text(lang.nativeName) }
                )
            }
        }
    }
}

@Composable
internal fun ModeSection(selected: AppMode, enabled: Boolean = true, onSelect: (AppMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ModeCard(selected == AppMode.SUGGEST, enabled, R.string.mode_suggest_title, R.string.mode_suggest_desc) { onSelect(AppMode.SUGGEST) }
        ModeCard(selected == AppMode.ROOT, enabled, R.string.mode_root_title, R.string.mode_root_desc) { onSelect(AppMode.ROOT) }
    }
}

@Composable
private fun ModeCard(selected: Boolean, enabled: Boolean, title: Int, desc: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = CardShape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Opens a GitHub issue with the device and result details pre-filled. Nothing is sent until the user submits it. */
@Composable
internal fun ReportButton(state: UiState) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    OutlinedButton(
        onClick = { uriHandler.openUri(ReportBuilder.issueUrl(context, state)) },
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(stringResource(R.string.report_button))
    }
}
