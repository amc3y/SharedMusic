package com.example.sharedmusic.ui

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
import com.example.sharedmusic.viewmodel.MusicViewModel

@Composable
fun MainScreen(
    viewModel: MusicViewModel = viewModel(),
    onOpenLibraryClick: () -> Unit,
    onOpenConnectionClick: () -> Unit
) {
    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addSong(it) }
    }

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    Column {
        Button(onClick = { musicPicker.launch("audio/*") }) {
            Text("Добавить музыку")
        }

        Button(onClick = { viewModel.toggleMusic() }) {
            Text(if (isPlaying) "Стоп" else "Послушать")
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