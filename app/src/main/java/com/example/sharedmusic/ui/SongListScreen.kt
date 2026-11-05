package com.example.sharedmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
        Text("Сохраняются на устройстве после перезапуска.")

        Spacer(modifier = Modifier.height(12.dp))

        if (songs.isEmpty()) {
            Text("Пока пусто. Нажми «Добавить музыку» на главном экране.")
        }

        LazyColumn {
            items(songs, key = { it.id }) { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.playSong(song) }
                    ) {
                        Text(song.title)
                        if (song.isRemote) {
                            Text("из комнаты")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.removeSong(song) }
                    ) {
                        Text("Удалить")
                    }
                }
            }
        }
    }
}
