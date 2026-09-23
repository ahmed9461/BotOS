package com.ahmed9461.botos.media

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahmed9461.botos.R
import com.ahmed9461.botos.data.LocalAvatarStore
import com.ahmed9461.botos.model.SavedBot

internal val LocalAvatarSnapshot = staticCompositionLocalOf { AvatarSnapshot() }
private val LocalConfigureAvatar = staticCompositionLocalOf<((SavedBot) -> Unit)?> { null }

@Composable
internal fun BotAvatar(bot: SavedBot, size: Dp = 52.dp, editable: Boolean = true) {
    val bitmap = LocalAvatarSnapshot.current.images[LocalAvatarStore.key(bot)]
    val configure = LocalConfigureAvatar.current
    val label = stringResource(R.string.avatar_edit, bot.title)
    val radius = if (size <= 40.dp) 13.dp else 17.dp
    Surface(shape = RoundedCornerShape(radius), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size).testTag("avatar-${bot.id}")) {
        Box(Modifier.fillMaxSize().then(if (editable && configure != null) Modifier.clickable(
            role = Role.Button, onClickLabel = label, onClick = { configure(bot) }) else Modifier),
            contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap = remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = stringResource(R.string.avatar_description, bot.title),
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().testTag("avatar-image-${bot.id}"))
            } else Text(bot.title.takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))) }.orEmpty(), style = if (size <= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("avatar-letter-${bot.id}"))
        }
    }
}

@Composable
internal fun AvatarHost(onNotice: (Int) -> Unit, vm: AvatarViewModel = viewModel(), content: @Composable () -> Unit) {
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by rememberUpdatedState(onNotice)
    LaunchedEffect(vm) { vm.notices.collect { notice(it) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), vm::picked)
    CompositionLocalProvider(LocalAvatarSnapshot provides snapshot, LocalConfigureAvatar provides vm::configure) {
        content()
        target?.let { bot ->
            AvatarOptions(bot, LocalAvatarStore.key(bot) in snapshot.custom, busy, vm::dismiss, {
                if (vm.preparePicker()) {
                    try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    catch (_: ActivityNotFoundException) { vm.pickerFailed() }
                    catch (_: SecurityException) { vm.pickerFailed() }
                }
            }, vm::reset)
        }
    }
}

@Composable
internal fun AvatarOptions(bot: SavedBot, hasCustom: Boolean, busy: Boolean, onDismiss: () -> Unit,
    onChoose: () -> Unit, onReset: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.avatar_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BotAvatar(bot, 64.dp, editable = false)
                Text(stringResource(R.string.avatar_local_only), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onChoose, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("avatar-choose")) {
                    Text(stringResource(R.string.avatar_choose))
                }
                if (hasCustom) OutlinedButton(onClick = onReset, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("avatar-reset")) { Text(stringResource(R.string.avatar_automatic)) }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp))
            }
        }, confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) } })
}
