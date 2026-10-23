package com.example.sharedmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharedmusic.viewmodel.MusicViewModel

@Composable
fun SongListScreen(
    viewModel: MusicViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        TextButton(onClick = onBackClick) {
            Text("← Назад")
        }

        Text("Список песен (${songs.size})")

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn {
            items(songs) { song ->
                Text(
                    text = song.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playSong(song) }
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}