package com.example.sharedmusic.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.sharedmusic.player.MusicPlayer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items



@Composable
fun MusicScreen() {
    val context = LocalContext.current

    //создаём интерфейс 1 раз, чтобы запомнил
    val musicPlayer = remember {
        MusicPlayer(context)
    }

    //выбираем песнИ
    var musicList by remember {
        mutableStateOf<List<Uri>>(emptyList())
    }

    //система выбора файла
    var musicPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            musicList = musicList + uris
        }
    DisposableEffect(Unit) {
        onDispose {
            musicPlayer.release()
        }
    }

    Column {
        Button(
            onClick = {
                musicPicker.launch(arrayOf("audio/*"))
            }
        ) {
            Text("Добавить музыку")
        }

        LazyColumn() {
            items(musicList) { uri ->
                Button(
                    onClick = {
                        musicPlayer.play(uri)
                    }
                ){
                    Text(
                        text = getFileName(context, uri)
                    )
                }
            }
        }

        Button(
            onClick = {
                musicPlayer.stop()
                }
        ) {
            Text("Стоп")
        }
    }
}

//Функция для нормальных названий файлов
fun getFileName(context: Context, uri: Uri): String {
    var fileName = "Неизвестная песня"

    val cursor = context.contentResolver.query(
        uri,
        null,
        null,
        null,
        null
    )

    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex =
                it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)

            if (nameIndex >= 0) {
                fileName = it.getString(nameIndex)
            }
        }
    }

    return fileName
}