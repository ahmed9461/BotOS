package com.ahmed9461.botos

import android.app.Application
import com.ahmed9461.botos.data.AccountRestoreStore
import com.ahmed9461.botos.tdlib.AndroidTelegramSession
import com.ahmed9461.botos.telegram.runtime.AccountCoordinator
import com.ahmed9461.botos.telegram.runtime.AccountSessionFactory
import com.ahmed9461.botos.telegram.runtime.BotProfiles
import com.ahmed9461.botos.telegram.runtime.TelegramFiles
import com.ahmed9461.botos.media.BotAvatars
import com.ahmed9461.botos.media.TelegramUploads
import com.ahmed9461.botos.telegram.runtime.BotConversations
import com.ahmed9461.botos.data.OutgoingMediaStore
import com.ahmed9461.botos.data.OutgoingUploadJournal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One account owner for the process, not one native client per screen or tab. */
class BotOsApplication : Application() {
    private val accountScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val account: AccountCoordinator by lazy {
        AccountCoordinator(
            TelegramBuildConfiguration.credentialsOrNull(),
            AccountSessionFactory { withContext(Dispatchers.IO) { AndroidTelegramSession.open(this@BotOsApplication) } },
            AccountRestoreStore(AccountRestoreStore.defaultFile(this), accountScope),
            accountScope,
        )
    }
    val botConversations: BotConversations by lazy { BotConversations(account, accountScope) }
    val telegramFiles: TelegramFiles by lazy { TelegramFiles(account, accountScope) }
    val botProfiles: BotProfiles by lazy { BotProfiles(account, telegramFiles, accountScope) }
    internal val botAvatars: BotAvatars by lazy { BotAvatars(this, account, telegramFiles, botProfiles, accountScope) }
    private val outgoingMedia: OutgoingMediaStore by lazy {
        OutgoingMediaStore(OutgoingMediaStore.defaultDirectory(noBackupFilesDir))
    }
    private val outgoingJournal: OutgoingUploadJournal by lazy {
        OutgoingUploadJournal(OutgoingMediaStore.defaultDirectory(noBackupFilesDir), outgoingMedia)
    }
    val telegramUploads: TelegramUploads by lazy {
        TelegramUploads(botConversations, outgoingMedia, outgoingJournal, accountScope)
    }
    override fun onCreate() {
        super.onCreate()
        telegramUploads // Subscribe to upload updates before restoring a previously consented session.
        accountScope.launch { account.restore() }
    }
}
