package com.ahmed9461.botos

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ahmed9461.botos.design.BotGlyph
import com.ahmed9461.botos.design.Glyph
import com.ahmed9461.botos.telegram.runtime.AccountCoordinator
import com.ahmed9461.botos.telegram.runtime.AccountIssue
import com.ahmed9461.botos.telegram.runtime.AccountState
import com.ahmed9461.botos.telegram.runtime.AuthStep

/** Pure, testable rendering of authoritative account state; no networking from a composable. */
@Composable
internal fun AccountScreen(
    state: AccountState,
    onBack: () -> Unit,
    onConnect: (Boolean) -> Unit,
    onSubmit: (AuthStep, String) -> Unit,
    onCancel: () -> Unit,
    onRetryIdentity: () -> Unit,
    onLogout: () -> Unit,
) {
    val takesInput = state.configured && state.step in AccountCoordinator.inputSteps
    SecureAccountWindow(takesInput)
    // Intentionally NOT rememberSaveable: secrets must not survive activity/process restoration.
    var input by remember(state.step) { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun submit() {
        if (state.busy || input.isBlank() || !takesInput) return
        val transientInput = input
        input = ""
        focus.clearFocus()
        onSubmit(state.step, transientInput)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("account-screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader(stringResource(R.string.account_title), stringResource(R.string.account_subtitle)) {
            IconButton(onClick = { input = ""; focus.clearFocus(); onBack() }, modifier = Modifier.testTag("account-back")) {
                BotGlyph(Glyph.BACK, stringResource(R.string.back))
            }
        }
        if (!state.configured) {
            Box(Modifier.testTag("account-unavailable")) { InfoCard(stringResource(R.string.account_unavailable)) }
        } else {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (state.step) {
                        AuthStep.CLOSED -> {
                            Text(stringResource(R.string.account_welcome), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.account_disclosure), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth().toggleable(consent, enabled = !state.busy, role = Role.Checkbox,
                                onValueChange = { consent = it }).padding(vertical = 6.dp).testTag("account-consent"),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = consent, onCheckedChange = null)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.account_consent), style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(onClick = { onConnect(consent) }, enabled = consent && !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-connect"), shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.account_connect))
                            }
                        }
                        AuthStep.PHONE, AuthStep.CODE, AuthStep.PASSWORD, AuthStep.EMAIL, AuthStep.EMAIL_CODE -> {
                            val label = when (state.step) {
                                AuthStep.PHONE -> R.string.account_phone
                                AuthStep.CODE -> R.string.account_code
                                AuthStep.PASSWORD -> R.string.account_password
                                AuthStep.EMAIL -> R.string.account_email
                                else -> R.string.account_email_code
                            }
                            Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(when (state.step) {
                                AuthStep.PHONE -> R.string.account_phone_hint
                                AuthStep.PASSWORD -> R.string.account_password_hint
                                AuthStep.EMAIL -> R.string.account_email_hint
                                else -> R.string.account_code_hint
                            }), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(input, { input = it.take(1024) }, enabled = !state.busy,
                                singleLine = true, label = { Text(stringResource(label)) },
                                textStyle = LocalTextStyle.current.copy(textDirection = if (state.step == AuthStep.PASSWORD) TextDirection.Content else TextDirection.Ltr),
                                modifier = Modifier.fillMaxWidth().testTag("account-input"), shape = RoundedCornerShape(18.dp),
                                visualTransformation = if (state.step == AuthStep.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false,
                                    keyboardType = when (state.step) {
                                        AuthStep.PHONE -> KeyboardType.Phone
                                        AuthStep.EMAIL -> KeyboardType.Email
                                        AuthStep.PASSWORD -> KeyboardType.Password
                                        else -> KeyboardType.NumberPassword
                                    }),
                                keyboardActions = KeyboardActions(onDone = { submit() }))
                            Button(onClick = ::submit, enabled = input.isNotBlank() && !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-submit"), shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.account_continue))
                            }
                            TextButton(onClick = { input = ""; focus.clearFocus(); onCancel() }, enabled = !state.busy,
                                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("account-cancel")) { Text(stringResource(R.string.cancel)) }
                        }
                        AuthStep.READY -> {
                            Text(stringResource(R.string.account_connected), style = MaterialTheme.typography.titleLarge)
                            if (state.displayName.isNotBlank()) Text(state.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.account_connected_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.displayName.isEmpty()) TextButton(onClick = onRetryIdentity, enabled = !state.busy) {
                                Text(stringResource(R.string.account_retry))
                            }
                            OutlinedButton(onClick = { confirmLogout = true }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-logout"), shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.account_logout))
                            }
                        }
                        AuthStep.STARTING, AuthStep.PARAMETERS, AuthStep.LOGGING_OUT, AuthStep.CLOSING -> {
                            Text(stringResource(if (state.step == AuthStep.LOGGING_OUT || state.step == AuthStep.CLOSING)
                                R.string.account_closing else R.string.account_connecting), style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (!state.busy) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                        }
                        else -> {
                            Text(stringResource(R.string.account_extra_step), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(when (state.step) {
                                AuthStep.OTHER_DEVICE -> R.string.account_other_device
                                AuthStep.REGISTRATION_REQUIRED -> R.string.account_registration
                                AuthStep.PREMIUM_REQUIRED -> R.string.account_premium
                                else -> R.string.account_unsupported
                            }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onCancel, enabled = !state.busy) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                    if (state.busy && state.step in AccountCoordinator.inputSteps) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        state.issue?.let { issue ->
            val text = when (issue) {
                AccountIssue.CONFIGURATION -> R.string.account_unavailable
                AccountIssue.CONSENT -> R.string.account_consent_needed
                AccountIssue.INPUT -> R.string.account_bad_input
                AccountIssue.BUSY -> R.string.account_busy
                AccountIssue.CONNECTION -> R.string.account_connection_error
                AccountIssue.STORAGE -> R.string.account_storage_error
                AccountIssue.CLEANUP -> R.string.account_cleanup_error
                AccountIssue.STATE -> R.string.account_state_changed
            }
            InfoCard(stringResource(text))
        }
        Text(stringResource(R.string.account_privacy_note), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 20.dp))
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
        title = { Text(stringResource(R.string.account_logout)) }, text = { Text(stringResource(R.string.account_logout_confirm)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }, modifier = Modifier.testTag("account-confirm-logout")) {
            Text(stringResource(R.string.account_logout))
        } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } })
}

/** Preserve any pre-existing secure flag; do not weaken a host window's security on disposal. */
@Composable
private fun SecureAccountWindow(protect: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity, protect) {
        val window = activity?.window
        val wasSecure = window != null && window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (protect) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (protect && !wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
