package com.example.sharedmusic.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.ui.components.EqualizerIcon
import com.example.sharedmusic.ui.theme.SharedMusicTheme

// Экран профиля пользователя:
// — начальное состояние: форма входа (никнейм)
// — после входа: аватар, статистика, настройки, скачанные треки
@Composable
fun ProfileScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onOpenEqualizerClick: () -> Unit,
    onAddMusicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface

    // Состояние входа (по умолчанию — не вошёл)
    var isLoggedIn by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    val userId = remember { "SH-" + (1000..9999).random() }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Отступ от верхней шторки
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Шапка профиля
        Text(
            text = "Профиль",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = onSurface,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(20.dp))

        if (!isLoggedIn) {
            // Форма входа (начальное состояние)
            LoginContent(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                onLogin = {
                    if (nickname.isNotBlank()) {
                        isLoggedIn = true
                    }
                },
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                surface = surface
            )
        } else {
            // Профиль после входа
            ProfileContent(
                nickname = nickname,
                userId = userId,
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange,
                onOpenEqualizerClick = onOpenEqualizerClick,
                onAddMusicClick = onAddMusicClick,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                surface = surface
            )
        }
    }
}

// Форма входа: никнейм и кнопка «Войти»
@Composable
private fun LoginContent(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onLogin: () -> Unit,
    primaryColor: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    surface: Color
) {
    // Аватарка-заглушка
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(primaryColor.copy(alpha = 0.2f))
            .border(3.dp, primaryColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }

    Spacer(Modifier.height(20.dp))

    Text(
        text = "Добро пожаловать в SHARY",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    Text(
        text = "Введите никнейм, чтобы начать",
        style = MaterialTheme.typography.bodyMedium,
        color = onSurfaceVariant
    )

    Spacer(Modifier.height(24.dp))

    // Поле ввода никнейма
    OutlinedTextField(
        value = nickname,
        onValueChange = onNicknameChange,
        label = { Text("Никнейм") },
        placeholder = { Text("Например: morikifa") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            focusedLabelColor = primaryColor,
            cursorColor = primaryColor
        )
    )

    Spacer(Modifier.height(16.dp))

    // Кнопка «Войти»
    Button(
        onClick = onLogin,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        enabled = nickname.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
    ) {
        Text("Войти", fontWeight = FontWeight.Bold)
    }
}

// Содержимое профиля после входа: аватар, статистика, настройки, треки
@Composable
private fun ColumnScope.ProfileContent(
    nickname: String,
    userId: String,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onOpenEqualizerClick: () -> Unit,
    onAddMusicClick: () -> Unit,
    primaryColor: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    surface: Color
) {
    // Аватарка с инициалом
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(primaryColor.copy(alpha = 0.2f))
            .border(3.dp, primaryColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = nickname.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }

    Spacer(Modifier.height(10.dp))

    Text(
        text = nickname,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = onSurface
    )
    Text(
        text = "ID: #$userId • Локальный профиль",
        style = MaterialTheme.typography.bodySmall,
        color = onSurfaceVariant
    )

    Spacer(Modifier.height(18.dp))

    // Карточка статистики (все нули — начальное состояние)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = "0", label = "Треков")
            StatItem(value = "0 ч", label = "В комнатах")
            StatItem(value = "0", label = "Синхронов")
        }
    }

    Spacer(Modifier.height(20.dp))

    // Раздел «Настройки»
    Text(
        text = "Настройки",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = onSurface,
        modifier = Modifier.align(Alignment.Start)
    )

    Spacer(Modifier.height(10.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Переключатель темы
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Тёмная тема",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface
                    )
                    Text(
                        text = if (isDarkTheme) "Включена тёмная палитра" else "Включена светлая тема",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onThemeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primaryColor
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // Пункт «Эквалайзер»
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenEqualizerClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerIcon(tint = primaryColor, iconSize = 18.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Эквалайзер звука",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface
                        )
                        Text(
                            text = "5 полос, басы и объёмный звук",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                    }
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = onSurfaceVariant)
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // Раздел «Скачанные треки» (пуст — начальное состояние)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Скачанные треки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = onSurface
        )
        Button(
            onClick = onAddMusicClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
        ) {
            Text("+ Загрузить", style = MaterialTheme.typography.labelMedium)
        }
    }

    Spacer(Modifier.height(10.dp))

    // Пустое состояние: пока нет добавленных треков
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Text(
            text = "Пока нет добавленных треков",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )
    }
}

// Элемент статистики
@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Превью: форма входа (тёмная тема)
@Preview(name = "Профиль: Вход (Тёмная)", showBackground = true, backgroundColor = 0xFF212121)
@Composable
private fun ProfileScreenLoginDarkPreview() {
    SharedMusicTheme(darkTheme = true) {
        ProfileScreen(
            isDarkTheme = true,
            onThemeChange = {},
            onOpenEqualizerClick = {},
            onAddMusicClick = {}
        )
    }
}

// Превью: форма входа (светлая тема)
@Preview(name = "Профиль: Вход (Светлая)", showBackground = true)
@Composable
private fun ProfileScreenLoginLightPreview() {
    SharedMusicTheme(darkTheme = false) {
        ProfileScreen(
            isDarkTheme = false,
            onThemeChange = {},
            onOpenEqualizerClick = {},
            onAddMusicClick = {}
        )
    }
}