package com.ahmed9461.botos

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*

@Composable
fun BotOsApp(vm: WorkspaceViewModel = viewModel()) {
    val snapshot by vm.workspace.collectAsStateWithLifecycle()
    val selectedId by vm.selected.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val stack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() }, restore = { it.toMutableStateList() },
    )) { mutableStateListOf("workspace") }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val preferences = snapshot.workspace.preferences
    fun navigate(destination: String) { if (stack.lastOrNull() != destination) stack.add(destination) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun openBot(username: String) {
        val safe = BotNames.normalize(username) ?: return
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$safe"))) }
        catch (_: ActivityNotFoundException) { vm.notice(R.string.open_error) }
        catch (_: SecurityException) { vm.notice(R.string.open_error) }
    }
    LaunchedEffect(vm, context) {
        vm.effects.collect { effect ->
            when (effect) {
                is UiEffect.Notice -> snackbar.showSnackbar(context.getString(effect.stringId))
                is UiEffect.Saved -> if (stack.lastOrNull() == effect.origin) back()
            }
        }
    }
    BotOsTheme(preferences.theme, preferences.reduceMotion) {
        val motion = LocalMotionMillis.current
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            (context as? Activity)?.let { activity ->
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (stack.last() in listOf("workspace", "appearance")) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.widthIn(max = 540.dp)) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                                listOf("workspace" to Glyph.SPACE, "appearance" to Glyph.APPEARANCE).forEach { (route, glyph) ->
                                    NavigationBarItem(
                                        selected = stack.last() == route,
                                        onClick = { if (route == "workspace") { stack.clear(); stack.add("workspace") } else navigate(route) },
                                        icon = { BotGlyph(glyph) },
                                        label = { Text(stringResource(if (route == "workspace") R.string.workspace else R.string.appearance)) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavDisplay(
                backStack = stack,
                transitionSpec = { fadeIn(tween(motion)) togetherWith fadeOut(tween(motion)) },
                popTransitionSpec = { fadeIn(tween(motion)) togetherWith fadeOut(tween(motion)) },
                predictivePopTransitionSpec = { fadeIn(tween(motion)) togetherWith fadeOut(tween(motion)) },
                onBack = { back() },
                modifier = Modifier.fillMaxSize().padding(padding),
                entryProvider = { route ->
                    NavEntry(route) {
                        when {
                            route == "workspace" -> WorkspaceScreen(
                                snapshot, selectedId, timeline, draft, busy,
                                onSelect = vm::select, onAdd = { navigate("add") },
                                onEdit = { navigate("edit/$it") }, onDelete = vm::remove,
                                onMove = vm::move, onOpenTelegram = ::openBot,
                                onDraft = vm::updateDraft,
                                onSend = { vm.sendPreview(context.getString(R.string.preview_reply)) },
                                onAction = vm::activate,
                            )
                            route == "appearance" -> AppearanceScreen(preferences, busy || snapshot.loading || snapshot.failed, vm::setTheme, vm::setMotion)
                            route == "add" || route.startsWith("edit/") -> {
                                val id = route.takeIf { it.startsWith("edit/") }?.removePrefix("edit/")
                                val existing = snapshot.workspace.bots.firstOrNull { it.id == id }
                                BotEditor(existing, busy || snapshot.loading || snapshot.failed || (id != null && existing == null),
                                    onBack = ::back, onSave = { name, title -> vm.saveBot(id, name, title, route) })
                            }
                        }
                    }
                },
            )
        }
    }
}
