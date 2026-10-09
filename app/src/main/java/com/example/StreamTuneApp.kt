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
    }
}
