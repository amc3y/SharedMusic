package com.example.sharedmusic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharedmusic.viewmodel.MusicViewModel
import androidx.compose.runtime.getValue


@Composable
fun MainScreen(
    //Мозг для отрисовки экрана в разных ситуациях
    viewModel: MusicViewModel = viewModel(),
    //Открытие окна со списком песен
    onOpenLibraryClick: () -> Unit
){
    //Берём музыку с файловой системы
    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        //Передаём файл в мозг когда получаем
        viewModel.onMusicSelected(uri)
    }

    //Есл значение кнопки меняется - он её перерисует (кнопка стоп/пуск)
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column{

        //Добавление музыки
        Button(
            onClick = {
                musicPicker.launch("audio/*")
            }
        ) {
            Text("Добавить музыку")
        }

        //Play/Stop
        Button(
            onClick = {
                viewModel.toggleMusic()
            }
        ){
            Text(
                text = if (isPlaying) "Стоп" else "Послушать"
            )
        }

        //Кнопка перехода на второй экран
        Button(
            onClick = {
                onOpenLibraryClick()
            }
        ) {
            Text("Список песен")
        }
    }
}