package com.shieldstream.tv.settings

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.restartApp
import com.shieldstream.tv.ui.theme.DisplayFont

// Ajustes → copia de seguridad: exportar la configuración como QR/JSON e importarla en otro dispositivo.

@Composable
internal fun ExportConfigCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_export_configuration),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_export_configuration_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Text(
                localizedStringResource(StringsR.string.settings_ok_to_open),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Color.White else Color.Transparent,
            )
        }
    }
}

@Composable
internal fun ImportConfigCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_import_configuration),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    localizedStringResource(StringsR.string.settings_import_configuration_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted,
                )
            }
            Text(
                localizedStringResource(StringsR.string.settings_ok_to_open),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Color.White else Color.Transparent,
            )
        }
    }
}

@Composable
internal fun ExportConfigDialog(json: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .background(Color(0xFF14151F), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            Text(
                localizedStringResource(StringsR.string.settings_export_configuration),
                fontFamily = DisplayFont,
                fontSize = 30.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                localizedStringResource(StringsR.string.settings_export_configuration_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsMuted,
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(210.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    QrCode(json, Modifier.fillMaxWidth().height(186.dp))
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .background(Color(0xFF101119), RoundedCornerShape(10.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            json,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton(
                            text = localizedStringResource(
                                if (copied) StringsR.string.settings_copied else StringsR.string.settings_copy,
                            ),
                            onClick = {
                                clipboard.setText(AnnotatedString(json))
                                copied = true
                            },
                        )
                        TvButton(
                            text = localizedStringResource(StringsR.string.settings_share),
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvButton(text = localizedStringResource(StringsR.string.settings_close), onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun ImportConfigDialog(state: ImportUi, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .background(Color(0xFF14151F), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            Text(
                localizedStringResource(StringsR.string.settings_import_configuration),
                fontFamily = DisplayFont,
                fontSize = 30.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                localizedStringResource(StringsR.string.settings_import_configuration_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SettingsMuted,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .background(Color(0xFF101119), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        localizedStringResource(StringsR.string.settings_import_placeholder),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 13.sp,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(10.dp))
            val message = when {
                state.error != null -> state.error to SettingsRed
                state.applied && state.restartNeeded -> localizedStringResource(
                    StringsR.string.settings_import_done_restart,
                ) to SettingsGreen
                state.applied -> localizedStringResource(StringsR.string.settings_import_done) to SettingsGreen
                else -> null to SettingsMuted
            }
            if (message.first != null) {
                Text(message.first!!, style = MaterialTheme.typography.bodyMedium, color = message.second)
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = localizedStringResource(StringsR.string.settings_paste),
                    onClick = { clipboard.getText()?.text?.let { text = it } },
                )
                TvButton(
                    text = localizedStringResource(StringsR.string.settings_import_action),
                    enabled = text.isNotBlank(),
                    onClick = { onApply(text) },
                )
                if (state.applied && state.restartNeeded) {
                    TvButton(
                        text = localizedStringResource(StringsR.string.settings_restart_now),
                        onClick = { restartApp(context) },
                    )
                }
                Spacer(Modifier.weight(1f))
                TvButton(text = localizedStringResource(StringsR.string.settings_close), onClick = onDismiss)
            }
        }
    }
}
