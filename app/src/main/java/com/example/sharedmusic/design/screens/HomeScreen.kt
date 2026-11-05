package com.example.sharedmusic.design.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharedmusic.logic.viewmodel.MusicViewModel

@Composable
fun HomeScreen(
    viewModel: MusicViewModel = viewModel(),
    onOpenLibraryClick: () -> Unit,
    onOpenConnectionClick: () -> Unit
) {
    // Можно выбрать несколько файлов сразу
    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addSongs(uris)
        }
    }

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val songs by viewModel.songs.collectAsState()

    Column {
        Button(onClick = { musicPicker.launch("audio/*") }) {
            Text("Добавить музыку (можно несколько)")
        }

        Text("В библиотеке: ${songs.size}")

        Button(onClick = { viewModel.toggleMusic() }) {
            Text(if (isPlaying) "Пауза" else "Послушать")
        }

        if (currentSong != null) {
            Text("Сейчас: ${currentSong?.title}")
        }

        Button(onClick = onOpenLibraryClick) {
            Text("Список песен")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onOpenConnectionClick) {
            Text("Создать / Войти в комнату")
        }
    }
}
