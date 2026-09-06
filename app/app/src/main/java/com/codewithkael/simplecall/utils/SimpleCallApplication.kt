package com.codewithkael.simplecall.utils

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.util.UUID

@HiltAndroidApp
class SimpleCallApplication : Application() {
    companion object {
        val USER_ID = UUID.randomUUID().toString().substring(0,5)
        /** True while MainActivity is in the foreground (resumed, not paused). */
        @Volatile var isForegrounded: Boolean = false
        val sharedIncomingAudio = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    }
}