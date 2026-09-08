package com.codewithkael.simplecall.di

import android.content.Context
import com.codewithkael.simplecall.ai.SherpaOnnxTranscriber
import com.codewithkael.simplecall.ai.Transcriber
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideGson(): Gson {
        return Gson()
    }

    /**
     * TASK 2 — on-device ASR provider. Returns a REAL sherpa-onnx transcriber when
     * both (a) the sherpa classes are on the classpath (satya adds the AAR/dep) and
     * (b) the Whisper model is in filesDir/asr/; otherwise NoOpTranscriber. Either
     * way it is honest: no fake transcript is ever produced, and the app builds and
     * runs today with zero sherpa dependency. @Singleton so the same instance is
     * injected into ShieldService and ShieldViewModel, which both assign it to the
     * shared ShieldPipeline.transcriber. See docs/ONDEVICE_RUNBOOK.md §ASR.
     */
    @Provides
    @Singleton
    fun provideTranscriber(@ApplicationContext context: Context): Transcriber =
        SherpaOnnxTranscriber.createOrNoOp(context)
}
