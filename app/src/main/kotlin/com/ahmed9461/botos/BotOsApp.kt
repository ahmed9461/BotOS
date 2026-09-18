package com.ahmed9461.botos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.ahmed9461.botos.design.*
import com.ahmed9461.botos.model.*

/** One inset owner; each destination observes live state inside its own composition. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotOsApp(vm: WorkspaceViewModel = viewModel()) {
    val shellState by vm.workspace.collectAsStateWithLifecycle()
    val stack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() }, restore = { it.toMutableStateList() },
    )) { mutableStateListOf("workspace") }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalActivity.current
    val focus = LocalFocusManager.current
    val resources by rememberUpdatedState(LocalResources.current)
    val imeVisible = WindowInsets.isImeVisible
    fun navigate(destination: String) {
        focus.clearFocus()
        if (stack.lastOrNull() != destination) stack.add(destination)
    }
    fun selectRoot(destination: String) {
        focus.clearFocus()
        if (stack.lastOrNull() == destination) return
        stack.clear()
        stack.add("workspace")
        if (destination != "workspace") stack.add(destination)
    }
    fun back() { focus.clearFocus(); if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun openBot(username: String) {
        val safe = BotNames.normalize(username) ?: return
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$safe"))) }
        catch (_: ActivityNotFoundException) { vm.notice(R.string.open_error) }
        catch (_: SecurityException) { vm.notice(R.string.open_error) }
    }
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is UiEffect.Notice -> snackbar.showSnackbar(resources.getString(effect.stringId))
                is UiEffect.Saved -> if (stack.lastOrNull() == effect.origin) {
                    back()
                    selectRoot("workspace")
                }
            }
        }
    }
    val preferences = shellState.workspace.preferences
    BotOsTheme(preferences.theme, preferences.reduceMotion) {
        val motion = LocalMotionMillis.current
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
        // Union takes the maximum, not the sum. Children must not add IME/navigation padding.
        val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime)
        Scaffold(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(safeInsets),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!imeVisible && stack.last() in listOf("workspace", "library", "appearance")) {
                    BottomDock(stack.last(), ::selectRoot)
                }
            },
        ) { padding ->
            NavDisplay(
                backStack = stack,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                onBack = ::back,
                transitionSpec = { fadeIn(tween(motion)) togetherWith fadeOut(tween(motion)) },
                popTransitionSpec = { fadeIn(tween(motion)) togetherWith fadeOut(tween(motion)) },
                predictivePopTransitionSpec = { fadeIn(tween(motion)) togetherWith fadeOut(tween(motion)) },
                entryProvider = { route ->
                    NavEntry(route) {
                        // Do not capture a computed Preferences value here: retained entries need live State reads.
                        when {
                            route == "workspace" -> WorkspaceRoute(vm, { navigate("add") }, { navigate("edit/$it") }, ::openBot)
                            route == "library" -> LibraryRoute(vm, { navigate("add") }, { navigate("edit/$it") })
                            route == "appearance" -> AppearanceRoute(vm)
                            route == "add" || route.startsWith("edit/") -> EditorRoute(vm, route, ::back)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun WorkspaceRoute(vm: WorkspaceViewModel, add: () -> Unit, edit: (String) -> Unit, open: (String) -> Unit) {
    val state by vm.workspace.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val reply = stringResource(R.string.preview_reply)
    WorkspaceScreen(state, selected, timeline, draft, busy, vm::select, add, edit, vm::remove,
        vm::move, open, vm::updateDraft, { vm.sendPreview(reply) }, vm::activate)
}

@Composable
private fun AppearanceRoute(vm: WorkspaceViewModel) {
    val state by vm.workspace.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    AppearanceScreen(state.workspace.preferences, busy || state.loading || state.failed, vm::setTheme, vm::setMotion)
}

@Composable
private fun EditorRoute(vm: WorkspaceViewModel, route: String, back: () -> Unit) {
    val state by vm.workspace.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val id = route.takeIf { it.startsWith("edit/") }?.removePrefix("edit/")
    val bot = state.workspace.bots.firstOrNull { it.id == id }
    BotEditor(bot, busy || state.loading || state.failed || (id != null && bot == null), back,
        { username, title -> vm.saveBot(id, username, title, route) })
}

@Composable
private fun LibraryRoute(vm: WorkspaceViewModel, add: () -> Unit, edit: (String) -> Unit) {
    val state by vm.workspace.collectAsStateWithLifecycle()
    LibraryScreen(state, add, edit)
}

@Composable
private fun BottomDock(selected: String, onSelect: (String) -> Unit) {
    val destinations = listOf(Triple("workspace", R.string.workspace, Glyph.SPACE),
        Triple("library", R.string.library, Glyph.LIBRARY), Triple("appearance", R.string.appearance, Glyph.APPEARANCE))
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("bottom-dock"),
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.selectableGroup().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                destinations.forEach { (route, label, glyph) ->
                    val active = selected == route
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(Modifier.testTag("nav-$route").selectable(active, role = Role.Tab, onClick = { onSelect(route) })
                            .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            val tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            BotGlyph(glyph, modifier = Modifier.size(21.dp), tint = tint)
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = tint)
                        }
                    }
                }
            }
        }
    }
}
