package com.example.sharedmusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SongListScreen (
    onBackClick: () -> Unit
){
    Column {
        Text(text = "Здесь будет список песен")

        Button(
            onClick = {
                onBackClick()
            }
        ) {
            Text("Назад на главную")
        }
    }
}