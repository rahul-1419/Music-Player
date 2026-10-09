package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StreamTune", appName)
    }

    @Test
    fun `song duration formatted correctly`() {
        val song = Song(
            id = "Umqb9KENgmk",
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationSeconds = 262,
            webUrl = "https://www.youtube.com/watch?v=Umqb9KENgmk"
        )

        assertEquals("4:22", song.formattedDuration)
    }

    @Test
    fun `song with zero duration handles formatted correctly`() {
        val song = Song(
            id = "test",
            title = "Live Stream",
            artist = "Arijit Singh",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationSeconds = 0,
            webUrl = "https://www.youtube.com/watch?v=test"
        )

        assertEquals("--:--", song.formattedDuration)
    }
}
