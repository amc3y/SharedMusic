package com.example.sharedmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.sharedmusic.ui.MusicScreen
import com.example.sharedmusic.ui.theme.SharedMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SharedMusicTheme {
                MusicScreen()
            }
        }
    }
}