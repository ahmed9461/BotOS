package com.ahmed9461.botos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
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
    val focus = LocalFocusManager.current
    fun save() { if (valid && !busy) { focus.clearFocus(); onSave(username, title) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("editor-screen")) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor-back")) { BotGlyph(Glyph.BACK, stringResource(R.string.back)) }
            Text(stringResource(if (bot == null) R.string.add_bot else R.string.edit_bot), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.editor_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (title.isNotBlank()) Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                BotBadge(title)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(value = username, onValueChange = { username = it.take(100) }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.username)) }, placeholder = { Text(stringResource(R.string.username_example)) },
                modifier = Modifier.fillMaxWidth().testTag("bot-username"), shape = RoundedCornerShape(18.dp),
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
            OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.local_name)) }, placeholder = { Text(stringResource(R.string.local_name_example)) },
                modifier = Modifier.fillMaxWidth().testTag("bot-title"), shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
            Text(stringResource(R.string.bookmark_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = ::save, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).heightIn(min = 52.dp).testTag("save-bot"), shape = RoundedCornerShape(18.dp)) {
            Text(stringResource(if (busy) R.string.saving else R.string.save))
        }
    }
}

@Composable
internal fun AppearanceScreen(preferences: Preferences, disabled: Boolean, onTheme: (ThemeMode) -> Unit, onMotion: (Boolean) -> Unit) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.35f
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("appearance-screen")) {
        ScreenHeader(stringResource(R.string.appearance), stringResource(R.string.appearance_intro))
        Text(stringResource(R.string.theme_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (largeText) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { ThemeTile(it, preferences.theme == it, disabled, { onTheme(it) }, Modifier.fillMaxWidth(), horizontal = true) }
            }
        } else {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { ThemeTile(it, preferences.theme == it, disabled, { onTheme(it) }, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(stringResource(R.string.motion_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxWidth().testTag("motion-toggle")
                .toggleable(value = preferences.reduceMotion, enabled = !disabled, role = Role.Switch, onValueChange = onMotion)
                .padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.reduce_motion), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (preferences.reduceMotion) R.string.motion_reduced else R.string.motion_full),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("motion-state"))
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = preferences.reduceMotion, onCheckedChange = null, enabled = !disabled)
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(onClick = { aboutOpen = true }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotGlyph(Glyph.SPACE, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.about), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.refinement_version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BotGlyph(Glyph.LINK, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.privacy_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp))
    }
    if (aboutOpen) AlertDialog(onDismissRequest = { aboutOpen = false }, title = { Text(stringResource(R.string.about)) },
        text = { Text(stringResource(R.string.about_body)) }, confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun ThemeTile(mode: ThemeMode, selected: Boolean, disabled: Boolean, onClick: () -> Unit, modifier: Modifier, horizontal: Boolean = false) {
    val label = stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.system_theme; ThemeMode.LIGHT -> R.string.light_theme; ThemeMode.DARK -> R.string.dark_theme })
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        if (horizontal) {
            Row(Modifier.testTag("theme-${mode.name}").selectable(selected, enabled = !disabled, role = Role.RadioButton, onClick = onClick)
                .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemeMiniature(mode, Modifier.width(70.dp).height(56.dp))
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                RadioButton(selected, onClick = null, enabled = !disabled)
            }
        } else {
            Column(Modifier.testTag("theme-${mode.name}").selectable(selected, enabled = !disabled, role = Role.RadioButton, onClick = onClick)
                .padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ThemeMiniature(mode, Modifier.fillMaxWidth().height(76.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    if (selected) BotGlyph(Glyph.CHECK, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ThemeMiniature(mode: ThemeMode, modifier: Modifier) {
    val pale = Color(0xFFF4F2F8)
    val dark = Color(0xFF24262D)
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(if (mode == ThemeMode.DARK) dark else pale))
            Box(Modifier.weight(1f).fillMaxHeight().background(if (mode == ThemeMode.LIGHT) pale else dark))
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFB7A8DC)))
            Box(Modifier.fillMaxWidth(.9f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFD4CBDD)))
            Box(Modifier.fillMaxWidth(.6f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFB7A8DC)))
        }
    }
}
