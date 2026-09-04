package com.antai.app

import android.content.Context
import com.antai.app.data.local.AppDatabase
import com.antai.app.data.local.AuthPrefs
import com.antai.app.data.remote.ApiClient
import com.antai.app.realtime.WsClient
import com.antai.app.realtime.WsRouter

object AppContainer {
    lateinit var db: AppDatabase
    lateinit var prefs: AuthPrefs
    lateinit var api: ApiClient
    lateinit var ws: WsClient
    lateinit var router: WsRouter

    fun init(context: Context) {
        db = AppDatabase.create(context)
        prefs = AuthPrefs(context)
        api = ApiClient(prefs)
        router = WsRouter()
        ws = WsClient(prefs, router)
    }
}