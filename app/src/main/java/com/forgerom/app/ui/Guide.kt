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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgerom.app.R

private val GuideSteps = listOf(
    R.string.guide_step_1, R.string.guide_step_2, R.string.guide_step_3, R.string.guide_step_4,
    R.string.guide_step_5, R.string.guide_step_6, R.string.guide_step_7, R.string.guide_step_8
)

/** A read-only checklist of the general steps before installing a custom ROM. It changes nothing on the phone. */
@Composable
internal fun GuideScreen(onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GradientHeader {
                Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, bottom = 20.dp)) {
                    TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(stringResource(R.string.action_back))
                    }
                    Text(
                        stringResource(R.string.guide_title),
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(Modifier.h().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyMedium)
                GuideSteps.forEachIndexed { index, res ->
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(stringResource(res), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}
