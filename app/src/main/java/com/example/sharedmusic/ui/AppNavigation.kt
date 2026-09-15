package com.example.sharedmusic.ui

import androidx.compose.runtime.*

@Composable
fun AppNavigation() {
    //переменная-переключатель
    var currentScreen by remember { mutableStateOf("main")}

    //отображение экрана в зависимости от переменной
    if (currentScreen == "main") {
        MainScreen(
            onOpenLibraryClick = {
                //меняем значение кнопки при переходе на библиотеку
                currentScreen = "library"
            }
        )
    } else {
        SongListScreen (
            onBackClick = {
                //меняем значение кнопки при переходе на главную
                currentScreen = "main"
            }
        )
    }
}