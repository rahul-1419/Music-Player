package com.example

import android.app.Application
import com.example.data.local.MusicDatabase
import com.example.data.repository.MusicRepository
import com.example.extractor.YouTubeMusicExtractor

class StreamTuneApp : Application() {

    val database: MusicDatabase by lazy {
        MusicDatabase.getInstance(this)
    }

    val extractor: YouTubeMusicExtractor by lazy {
        YouTubeMusicExtractor()
    }

    val repository: MusicRepository by lazy {
        MusicRepository(database.musicDao(), extractor)
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("StreamTune", "Global uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
