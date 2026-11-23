package com.forgerom.app

import android.app.Application

class ForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
