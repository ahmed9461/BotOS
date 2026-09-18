package com.ahmed9461.botos.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ahmed9461.botos.telegram.runtime.ConnectionPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/** A single process-owned store, separate from bookmarks; contains permission, never credentials. */
class AccountRestoreStore(file: File, scope: CoroutineScope) : ConnectionPreferences {
    // Keep the caller's Job/lifetime but never inherit its UI dispatcher for disk operations.
    private val store = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO), produceFile = { file })
    override suspend fun mayRestore(): Boolean = store.data.first()[MAY_RESTORE] ?: false
    override suspend fun setMayRestore(value: Boolean) { store.edit { it[MAY_RESTORE] = value } }

    companion object {
        private val MAY_RESTORE = booleanPreferencesKey("may_restore_account_v1")
        fun defaultFile(context: Context) = File(context.noBackupFilesDir, "account_restore.preferences_pb")
    }
}
