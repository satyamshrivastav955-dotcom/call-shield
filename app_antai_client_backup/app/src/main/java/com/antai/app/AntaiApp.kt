package com.antai.app

import android.app.Application

class AntaiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}