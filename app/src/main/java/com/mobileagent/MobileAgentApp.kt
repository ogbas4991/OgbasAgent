package com.mobileagent

import android.app.Application
import com.mobileagent.data.Prefs
import com.mobileagent.telegram.TelegramBotManager

class MobileAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)

        if (Prefs.telegramEnabled && Prefs.telegramBotToken.isNotBlank()) {
            TelegramBotManager.start(this)
        }
    }
}