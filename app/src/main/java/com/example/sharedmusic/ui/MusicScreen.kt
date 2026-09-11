package com.example.sharedmusic.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.sharedmusic.player.MusicPlayer

@Composable
fun MusicScreen() {
    val context = LocalContext.current

    //создаём интерфейс 1 раз, чтобы запомнил
    val musicPlayer = remember {
        MusicPlayer(context)
    }

    //выбираем песню
    var selectMusic by rememberSaveable {
        mutableStateOf<Uri?>(null)
    }

    //система выбора файла
    var musicPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            selectMusic = uri
        }
    DisposableEffect(Unit) {
        onDispose {
            musicPlayer.release()
        }
    }

    Column {
        Button(
            onClick = {
                musicPicker.launch("audio/*")
            }
        ) {
            Text("Добавить музыку")
        }

        Button(
            onClick = {
                selectMusic?.let { uri ->
                    musicPlayer.play(uri)
                }
            }
        ){
            Text("Послушать")
        }
    }
}