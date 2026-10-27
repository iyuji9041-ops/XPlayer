package com.Shyam.xplayer

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class XPlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize yt-dlp and FFmpeg native engines asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@XPlayerApplication)
                FFmpeg.getInstance().init(this@XPlayerApplication)
                Log.i("XPlayerApp", "YoutubeDL and FFmpeg native C++ engines initialized successfully")
            } catch (e: Throwable) {
                Log.w("XPlayerApp", "YoutubeDL initialization warning: ${e.message}")
            }
        }
    }
}
