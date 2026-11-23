package com.forgerom.app.ui

import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgerom.app.R
import com.forgerom.app.UiState
import com.forgerom.app.device.CheckStatus
import com.forgerom.app.device.PhoneCheck

/** Reads a few settings from this phone and shows whether it is ready. Nothing is changed or sent. */
@Composable
internal fun DetailsScreen(state: UiState, a: Actions) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GradientHeader {
                Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, bottom = 20.dp)) {
                    TextButton(onClick = a.onCloseDetails, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.details_title),
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.h().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.details_intro), style = MaterialTheme.typography.bodyMedium)
                if (state.phoneChecking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                    Text(stringResource(R.string.details_checking), style = MaterialTheme.typography.bodySmall)
                }
                state.phone?.let { info ->
                    info.checks.forEach { CheckCard(it) }

                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.details_technical),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(info.reportText, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = a.onRefreshDetails,
                            enabled = !state.phoneChecking,
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(stringResource(R.string.details_refresh)) }
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(info.reportText))
                                Toast.makeText(context, R.string.details_copied, Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(stringResource(R.string.details_copy)) }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

@Composable
private fun CheckCard(check: PhoneCheck) {
    val (symbol, color) = when (check.status) {
        CheckStatus.OK -> "✓" to Color(0xFF1E9E5A)
        CheckStatus.WARN -> "⚠" to Color(0xFFE08A00)
        CheckStatus.INFO -> "ℹ" to MaterialTheme.colorScheme.primary
        CheckStatus.UNKNOWN -> "?" to MaterialTheme.colorScheme.outline
    }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(symbol, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text(stringResource(check.title), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    check.value?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium) }
                }
                check.hint?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
