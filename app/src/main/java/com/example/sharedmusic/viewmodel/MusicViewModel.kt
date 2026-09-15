//Создан для того, что бы приложение работала, в разных услових (при запуске
// мощной игры, в фоне, при попытке поворота экрана и тд.)
package com.example.sharedmusic.viewmodel

import android.app.Application //Паспорт приложения
import android.net.Uri  //Хранение путей к файлам
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel //работает при нехватке памяти и тд
import com.example.sharedmusic.player.MusicPlayer //наш класс плеера
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicViewModel(application: Application) : AndroidViewModel(application){
    //экземпляр плеера
    private val musicPlayer = MusicPlayer(application)
    //выбор песни
    var selectedMusicUri = mutableStateOf<Uri?>(null)
        private set

    //Музыка играет/не играет
    private val _isPlaying = MutableStateFlow(false)
    //Просмотр значения /\ не меняя его
    val isPlaying = _isPlaying.asStateFlow()

    //выбор песни
    fun onMusicSelected(uri: Uri?) {
        selectedMusicUri.value=uri
    }

    //запуск музыки
    fun playMusic() {
        selectedMusicUri.value?.let { uri ->
            musicPlayer.play(uri)
        }
        _isPlaying.value = true
    }

    //Стоп музыка
    fun stopMusic() {
        musicPlayer.stop()
        _isPlaying.value=false
    }

    //Функция для работы с 1-й кнопки
    fun  toggleMusic() {
        if(_isPlaying.value) {
            stopMusic()
        } else {
            playMusic()
        }
    }


    //уничтожение приложения из=за ненадобности
    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }
}