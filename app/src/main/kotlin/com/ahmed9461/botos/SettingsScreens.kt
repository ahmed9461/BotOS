package com.ahmed9461.botos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*

@Composable
internal fun BotEditor(bot: SavedBot?, busy: Boolean, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var username by rememberSaveable(bot?.id) { mutableStateOf(bot?.username.orEmpty()) }
    var title by rememberSaveable(bot?.id) { mutableStateOf(bot?.title.orEmpty()) }
    val valid = BotNames.normalize(username) != null && BotNames.validTitle(title)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { BotGlyph(Glyph.BACK, stringResource(R.string.back)) }
            Text(stringResource(if (bot == null) R.string.add_bot else R.string.edit_bot), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it.take(100) }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.username)) }, placeholder = { Text(stringResource(R.string.username_example)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false))
                OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.local_name)) }, placeholder = { Text(stringResource(R.string.local_name_example)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                Button(onClick = { onSave(username, title) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(stringResource(if (busy) R.string.saving else R.string.save))
                }
            }
        }
        InfoCard(stringResource(R.string.bookmark_notice))
    }
}

@Composable
internal fun AppearanceScreen(preferences: Preferences, disabled: Boolean, onTheme: (ThemeMode) -> Unit, onMotion: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.your_mood), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.appearance_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = preferences.theme == mode
                    val label = stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.system_theme; ThemeMode.LIGHT -> R.string.light_theme; ThemeMode.DARK -> R.string.dark_theme })
                    Row(Modifier.fillMaxWidth().selectable(selected, enabled = !disabled, role = Role.RadioButton, onClick = { onTheme(mode) }).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = null, enabled = !disabled)
                        Spacer(Modifier.width(14.dp)); Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reduce_motion), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.reduce_motion_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp)); Switch(preferences.reduceMotion, onCheckedChange = onMotion, enabled = !disabled)
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.privacy_hint), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.preview_version), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
