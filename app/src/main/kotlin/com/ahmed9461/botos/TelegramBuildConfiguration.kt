package com.ahmed9461.botos

import com.ahmed9461.botos.telegram.runtime.TelegramAppCredentials

/** Configuration is not consent and must never automatically open a native session. */
internal object TelegramBuildConfiguration {
    fun credentialsOrNull(): TelegramAppCredentials? =
        if (!BuildConfig.TELEGRAM_CONFIGURED) null
        else TelegramAppCredentials(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
}
