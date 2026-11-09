package com.example.sharedmusic.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.example.sharedmusic.design.navigation.SharyTab
import com.example.sharedmusic.design.theme.SharyDimens
import com.example.sharedmusic.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Режимы сортировки и фильтрации песен в строке поиска
enum class SongSortMode(val labelResId: Int) {
    DEFAULT(R.string.action_sort_default),
    SIZE_DESC(R.string.action_sort_size_desc),
    SIZE_ASC(R.string.action_sort_size_asc),
    DATE_DESC(R.string.action_sort_date_desc),
    DATE_ASC(R.string.action_sort_date_asc),
    DURATION_DESC(R.string.action_sort_duration_desc),
    DURATION_ASC(R.string.action_sort_duration_asc),
    TITLE_ASC(R.string.action_sort_title_asc),
    TITLE_DESC(R.string.action_sort_title_desc),
    ONLY_MP3(R.string.action_sort_only_mp3),
    NON_MP3(R.string.action_sort_non_mp3);

    fun next(): SongSortMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

private data class SharyLogoBar(
    val cx: Float,
    val yTop: Float,
    val yBottom: Float,
    val isSecondary: Boolean
)

private val SHARY_LOGO_BARS = listOf(
    SharyLogoBar(0.1683f, 0.4407f, 0.6208f, true),
    SharyLogoBar(0.2371f, 0.3315f, 0.5183f, false),
    SharyLogoBar(0.2371f, 0.5654f, 0.7284f, true),
    SharyLogoBar(0.3082f, 0.2611f, 0.5466f, false),
    SharyLogoBar(0.3082f, 0.6269f, 0.7932f, true),
    SharyLogoBar(0.3820f, 0.2195f, 0.3930f, false),
    SharyLogoBar(0.3820f, 0.4396f, 0.5771f, false),
    SharyLogoBar(0.3820f, 0.6657f, 0.8309f, true),
    SharyLogoBar(0.4569f, 0.1984f, 0.3775f, false),
    SharyLogoBar(0.4569f, 0.4706f, 0.5998f, false),
    SharyLogoBar(0.4569f, 0.6885f, 0.8509f, false),
    SharyLogoBar(0.5324f, 0.1990f, 0.3947f, false),
    SharyLogoBar(0.5324f, 0.4945f, 0.6242f, false),
    SharyLogoBar(0.5324f, 0.6790f, 0.8509f, false),
    SharyLogoBar(0.6085f, 0.2262f, 0.4202f, false),
    SharyLogoBar(0.6085f, 0.5100f, 0.8287f, false),
    SharyLogoBar(0.6840f, 0.2783f, 0.4590f, true),
    SharyLogoBar(0.6840f, 0.5344f, 0.7894f, false),
    SharyLogoBar(0.7556f, 0.3304f, 0.5177f, true),
    SharyLogoBar(0.7556f, 0.5649f, 0.7256f, false),
    SharyLogoBar(0.8244f, 0.4351f, 0.6170f, false)
)

@Composable
fun SharyBottomTabIcon(
    tab: SharyTab,
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.BottomNavIconSize)
) {
    when (tab) {
        SharyTab.LIBRARY -> SharyLibraryIcon(tint = tint, modifier = modifier)
        SharyTab.ROOM -> SharyRoomIcon(tint = tint, modifier = modifier)
        SharyTab.SETTINGS -> SharySettingsIcon(tint = tint, modifier = modifier)
        SharyTab.PROFILE -> SharyProfileIcon(tint = tint, modifier = modifier)
    }
}

// Иконка 1 (Библиотека): чистый значок альбома в стопке БЕЗ ноты внутри (передний квадрат + нижний левый уголок)
@Composable
fun SharyLibraryIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.BottomNavIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.85f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val backLayer = Path().apply {
            moveTo(4.0f * u, 8.2f * u)
            lineTo(4.0f * u, 17.4f * u)
            cubicTo(
                4.0f * u, 19.0f * u,
                5.0f * u, 20.0f * u,
                6.6f * u, 20.0f * u
            )
            lineTo(15.8f * u, 20.0f * u)
        }
        drawPath(path = backLayer, color = tint, style = stroke)

        drawRoundRect(
            color = tint,
            topLeft = Offset(7.4f * u, 4.0f * u),
            size = Size(12.6f * u, 12.6f * u),
            cornerRadius = CornerRadius(2.6f * u, 2.6f * u),
            style = stroke
        )
    }
}

// Иконка 2 (Комната): два человека рядом
@Composable
fun SharyRoomIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.BottomNavIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.85f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        drawCircle(
            color = tint,
            radius = 3.2f * u,
            center = Offset(9.0f * u, 8.2f * u),
            style = stroke
        )
        val frontShoulders = Path().apply {
            moveTo(3.2f * u, 19.2f * u)
            cubicTo(
                3.6f * u, 14.8f * u,
                6.0f * u, 13.5f * u,
                9.0f * u, 13.5f * u
            )
            cubicTo(
                12.0f * u, 13.5f * u,
                14.4f * u, 14.8f * u,
                14.8f * u, 19.2f * u
            )
        }
        drawPath(path = frontShoulders, color = tint, style = stroke)

        val secondaryHead = Path().apply {
            addArc(
                oval = Rect(
                    left = 13.3f * u,
                    top = 5.6f * u,
                    right = 18.7f * u,
                    bottom = 11.0f * u
                ),
                startAngleDegrees = -115f,
                sweepAngleDegrees = 230f
            )
        }
        drawPath(path = secondaryHead, color = tint, style = stroke)

        val backShoulders = Path().apply {
            moveTo(16.2f * u, 13.7f * u)
            cubicTo(
                18.7f * u, 13.9f * u,
                20.5f * u, 15.4f * u,
                21.0f * u, 19.0f * u
            )
        }
        drawPath(path = backShoulders, color = tint, style = stroke)
    }
}

// Иконка 3 (Настройки): шестигранная гайка
@Composable
fun SharySettingsIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.BottomNavIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.85f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.cornerPathEffect(1.6f * u)
        )

        val cx = 12f * u
        val cy = 12f * u
        val r = 8.4f * u

        val hex = Path().apply {
            for (i in 0 until 6) {
                val angle = (-PI / 2.0) + i * (PI / 3.0)
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path = hex, color = tint, style = stroke)

        drawCircle(
            color = tint,
            radius = 2.8f * u,
            center = Offset(cx, cy),
            style = Stroke(width = 1.85f * u)
        )
    }
}

// Иконка 4 (Профиль): контур одного человека
@Composable
fun SharyProfileIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.BottomNavIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.85f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        drawCircle(
            color = tint,
            radius = 3.6f * u,
            center = Offset(12f * u, 8.0f * u),
            style = stroke
        )
        val shoulders = Path().apply {
            moveTo(5.0f * u, 19.4f * u)
            cubicTo(
                5.5f * u, 14.8f * u,
                8.3f * u, 13.6f * u,
                12.0f * u, 13.6f * u
            )
            cubicTo(
                15.7f * u, 13.6f * u,
                18.5f * u, 14.8f * u,
                19.0f * u, 19.4f * u
            )
        }
        drawPath(path = shoulders, color = tint, style = stroke)
    }
}

// Векторный логотип SHARY (21 капсула с буквой "S")
@Composable
fun SharyLogoMark(
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier.size(SharyDimens.TopBarIconSize)
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f

        val scale = (s * 0.88f) / 0.70f
        val barWidth = 0.041f * scale
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (bar in SHARY_LOGO_BARS) {
            val xc = ox + s * 0.5f + (bar.cx - 0.49635f) * scale
            val yt = oy + s * 0.5f + (bar.yTop - 0.52465f) * scale
            val yb = oy + s * 0.5f + (bar.yBottom - 0.52465f) * scale
            val barColor = if (bar.isSecondary) secondaryColor else primaryColor

            drawRoundRect(
                color = barColor,
                topLeft = Offset(xc - barWidth / 2f, yt),
                size = Size(barWidth, (yb - yt).coerceAtLeast(barWidth)),
                cornerRadius = radius
            )
        }
    }
}

// Иконка лупы поиска
@Composable
fun SharySearchIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.TopBarIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val strokeWidth = 2.0f * u

        drawCircle(
            color = tint,
            radius = 6.8f * u,
            center = Offset(10.6f * u, 10.6f * u),
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = tint,
            start = Offset(15.6f * u, 15.6f * u),
            end = Offset(20.2f * u, 20.2f * u),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

// Стрелка вниз ("⌄") для правого верхнего уголка сворачивания плеера обратно к списку песен
@Composable
fun SharyChevronDownIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val chevron = Path().apply {
            moveTo(5.2f * u, 8.8f * u)
            lineTo(12.0f * u, 15.6f * u)
            lineTo(18.8f * u, 8.8f * u)
        }
        drawPath(
            path = chevron,
            color = tint,
            style = Stroke(
                width = 2.6f * u,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// Маленький крестик для удаления записи из недавних поисков и очистки строки поиска
@Composable
fun SharySmallCrossIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val sw = 2.2f * u
        drawLine(
            color = tint,
            start = Offset(6.5f * u, 6.5f * u),
            end = Offset(17.5f * u, 17.5f * u),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(17.5f * u, 6.5f * u),
            end = Offset(6.5f * u, 17.5f * u),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

// Иконка сортировки и фильтрации справа в строке поиска:
// по умолчанию статичные стрелки вверх и вниз (⇅), а при нажатии — значок выбранной сортировки + стрелка ↑ или ↓
@Composable
fun SharySortModeIcon(
    mode: SongSortMode,
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.SubTabIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.8f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (mode) {
            SongSortMode.DEFAULT -> {
                // Изначальные статичные две стрелки: вверх и вниз
                drawVerticalArrow(x = 8.2f * u, topY = 4.6f * u, bottomY = 19.4f * u, pointUp = true, u = u, tint = tint)
                drawVerticalArrow(x = 15.8f * u, topY = 4.6f * u, bottomY = 19.4f * u, pointUp = false, u = u, tint = tint)
            }

            SongSortMode.SIZE_DESC, SongSortMode.SIZE_ASC -> {
                // Значок веса файла (гирька/диск) слева + стрелка ↑/↓ справа
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3.0f * u, 9.6f * u),
                    size = Size(8.8f * u, 9.2f * u),
                    cornerRadius = CornerRadius(2.0f * u, 2.0f * u),
                    style = stroke
                )
                drawCircle(
                    color = tint,
                    radius = 2.0f * u,
                    center = Offset(7.4f * u, 7.2f * u),
                    style = stroke
                )
                drawVerticalArrow(
                    x = 17.8f * u,
                    topY = 5.0f * u,
                    bottomY = 19.0f * u,
                    pointUp = mode == SongSortMode.SIZE_ASC,
                    u = u,
                    tint = tint
                )
            }

            SongSortMode.DATE_DESC, SongSortMode.DATE_ASC -> {
                // Значок даты добавления (часы) слева + стрелка ↑/↓ справа
                val cx = 7.6f * u
                val cy = 12.2f * u
                drawCircle(
                    color = tint,
                    radius = 4.8f * u,
                    center = Offset(cx, cy),
                    style = stroke
                )
                val hands = Path().apply {
                    moveTo(cx, cy - 2.6f * u)
                    lineTo(cx, cy)
                    lineTo(cx + 2.2f * u, cy + 1.4f * u)
                }
                drawPath(path = hands, color = tint, style = stroke)
                drawVerticalArrow(
                    x = 17.8f * u,
                    topY = 5.0f * u,
                    bottomY = 19.0f * u,
                    pointUp = mode == SongSortMode.DATE_ASC,
                    u = u,
                    tint = tint
                )
            }

            SongSortMode.DURATION_DESC, SongSortMode.DURATION_ASC -> {
                // Значок длительности проигрывания (звуковые полоски разной высоты) слева + стрелка ↑/↓ справа
                val sw = 1.75f * u
                drawLine(tint, Offset(3.4f * u, 9.5f * u), Offset(3.4f * u, 15.5f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(6.8f * u, 6.5f * u), Offset(6.8f * u, 18.5f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(10.2f * u, 10.5f * u), Offset(10.2f * u, 14.5f * u), sw, StrokeCap.Round)
                drawVerticalArrow(
                    x = 17.8f * u,
                    topY = 5.0f * u,
                    bottomY = 19.0f * u,
                    pointUp = mode == SongSortMode.DURATION_ASC,
                    u = u,
                    tint = tint
                )
            }

            SongSortMode.TITLE_ASC, SongSortMode.TITLE_DESC -> {
                // Значок алфавита (буква А) слева + стрелка ↑/↓ справа
                val letterA = Path().apply {
                    moveTo(3.2f * u, 18.2f * u)
                    lineTo(7.4f * u, 6.2f * u)
                    lineTo(11.6f * u, 18.2f * u)
                    moveTo(4.7f * u, 14.2f * u)
                    lineTo(10.1f * u, 14.2f * u)
                }
                drawPath(path = letterA, color = tint, style = stroke)
                drawVerticalArrow(
                    x = 17.8f * u,
                    topY = 5.0f * u,
                    bottomY = 19.0f * u,
                    pointUp = mode == SongSortMode.TITLE_ASC,
                    u = u,
                    tint = tint
                )
            }

            SongSortMode.ONLY_MP3, SongSortMode.NON_MP3 -> {
                // Значок аудиофайла (только MP3 или кроме MP3 с диагональной чертой) + стрелка ↑/↓ справа
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(2.8f * u, 6.2f * u),
                    size = Size(9.2f * u, 11.8f * u),
                    cornerRadius = CornerRadius(2.0f * u, 2.0f * u),
                    style = stroke
                )
                drawCircle(
                    color = tint,
                    radius = 1.4f * u,
                    center = Offset(6.7f * u, 14.0f * u),
                    style = Fill
                )
                drawLine(
                    color = tint,
                    start = Offset(8.0f * u, 14.0f * u),
                    end = Offset(8.0f * u, 9.2f * u),
                    strokeWidth = 1.5f * u,
                    cap = StrokeCap.Round
                )
                if (mode == SongSortMode.NON_MP3) {
                    drawLine(
                        color = tint,
                        start = Offset(2.4f * u, 18.4f * u),
                        end = Offset(12.6f * u, 5.8f * u),
                        strokeWidth = 1.7f * u,
                        cap = StrokeCap.Round
                    )
                }
                drawVerticalArrow(
                    x = 17.8f * u,
                    topY = 5.0f * u,
                    bottomY = 19.0f * u,
                    pointUp = mode == SongSortMode.ONLY_MP3,
                    u = u,
                    tint = tint
                )
            }
        }
    }
}

private fun DrawScope.drawVerticalArrow(
    x: Float,
    topY: Float,
    bottomY: Float,
    pointUp: Boolean,
    u: Float,
    tint: Color
) {
    val sw = 1.8f * u
    drawLine(
        color = tint,
        start = Offset(x, topY),
        end = Offset(x, bottomY),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
    val head = Path().apply {
        if (pointUp) {
            moveTo(x - 2.6f * u, topY + 2.8f * u)
            lineTo(x, topY)
            lineTo(x + 2.6f * u, topY + 2.8f * u)
        } else {
            moveTo(x - 2.6f * u, bottomY - 2.8f * u)
            lineTo(x, bottomY)
            lineTo(x + 2.6f * u, bottomY - 2.8f * u)
        }
    }
    drawPath(
        path = head,
        color = tint,
        style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

// Иконка 1-й подвкладки в Библиотеке: нота + справа снизу значок загрузки
@Composable
fun SharyNoteDownloadedIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.SubTabIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f

        drawCircle(
            color = tint,
            radius = 2.8f * u,
            center = Offset(7.4f * u, 16.5f * u),
            style = Fill
        )
        val stemAndFlag = Path().apply {
            moveTo(9.9f * u, 16.4f * u)
            lineTo(9.9f * u, 4.6f * u)
            cubicTo(
                13.0f * u, 5.0f * u,
                15.0f * u, 6.5f * u,
                15.4f * u, 9.0f * u
            )
        }
        drawPath(
            path = stemAndFlag,
            color = tint,
            style = Stroke(
                width = 1.9f * u,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val badgeStroke = 1.75f * u
        drawLine(
            color = tint,
            start = Offset(17.5f * u, 12.0f * u),
            end = Offset(17.5f * u, 18.2f * u),
            strokeWidth = badgeStroke,
            cap = StrokeCap.Round
        )
        val arrowHead = Path().apply {
            moveTo(15.0f * u, 15.9f * u)
            lineTo(17.5f * u, 18.5f * u)
            lineTo(20.0f * u, 15.9f * u)
        }
        drawPath(
            path = arrowHead,
            color = tint,
            style = Stroke(
                width = badgeStroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawLine(
            color = tint,
            start = Offset(14.6f * u, 20.6f * u),
            end = Offset(20.4f * u, 20.6f * u),
            strokeWidth = badgeStroke,
            cap = StrokeCap.Round
        )
    }
}

// Иконка сердечка
@Composable
fun SharyHeartIcon(
    filled: Boolean,
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.SongActionIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val heartPath = Path().apply {
            moveTo(12.0f * u, 20.1f * u)
            cubicTo(6.8f * u, 16.0f * u, 3.3f * u, 12.7f * u, 3.3f * u, 8.7f * u)
            cubicTo(3.3f * u, 5.8f * u, 5.5f * u, 3.8f * u, 8.2f * u, 3.8f * u)
            cubicTo(9.9f * u, 3.8f * u, 11.3f * u, 4.6f * u, 12.0f * u, 5.9f * u)
            cubicTo(12.7f * u, 4.6f * u, 14.1f * u, 3.8f * u, 15.8f * u, 3.8f * u)
            cubicTo(18.5f * u, 3.8f * u, 20.7f * u, 5.8f * u, 20.7f * u, 8.7f * u)
            cubicTo(20.7f * u, 12.7f * u, 17.2f * u, 16.0f * u, 12.0f * u, 20.1f * u)
            close()
        }

        if (filled) {
            drawPath(path = heartPath, color = tint, style = Fill)
        } else {
            drawPath(
                path = heartPath,
                color = tint,
                style = Stroke(
                    width = 1.85f * u,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

// Иконка корзины для удаления песни
@Composable
fun SharyTrashIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.SongActionIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.75f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        drawLine(
            color = tint,
            start = Offset(4.4f * u, 6.6f * u),
            end = Offset(19.6f * u, 6.6f * u),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        val handle = Path().apply {
            moveTo(9.2f * u, 6.5f * u)
            lineTo(9.2f * u, 4.7f * u)
            cubicTo(9.2f * u, 4.0f * u, 9.8f * u, 3.6f * u, 10.6f * u, 3.6f * u)
            lineTo(13.4f * u, 3.6f * u)
            cubicTo(14.2f * u, 3.6f * u, 14.8f * u, 4.0f * u, 14.8f * u, 4.7f * u)
            lineTo(14.8f * u, 6.5f * u)
        }
        drawPath(path = handle, color = tint, style = stroke)

        val body = Path().apply {
            moveTo(6.4f * u, 6.8f * u)
            lineTo(7.3f * u, 18.6f * u)
            cubicTo(7.4f * u, 19.7f * u, 8.2f * u, 20.4f * u, 9.3f * u, 20.4f * u)
            lineTo(14.7f * u, 20.4f * u)
            cubicTo(15.8f * u, 20.4f * u, 16.6f * u, 19.7f * u, 16.7f * u, 18.6f * u)
            lineTo(17.6f * u, 6.8f * u)
        }
        drawPath(path = body, color = tint, style = stroke)

        drawLine(tint, Offset(10.3f * u, 10.2f * u), Offset(10.6f * u, 16.8f * u), 1.55f * u, StrokeCap.Round)
        drawLine(tint, Offset(13.7f * u, 10.2f * u), Offset(13.4f * u, 16.8f * u), 1.55f * u, StrokeCap.Round)
    }
}

// Увеличенный в 2 раза значок пустого списка (БЕЗ стрелки и БЕЗ текста)
@Composable
fun SharyEmptyPlaylistEmblem(
    primaryTint: Color,
    accentTint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.EmptyStateIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 96f
        val cardStroke = Stroke(
            width = 2.6f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Крупная скруглённая карточка трека со знаком вопроса по центру (без стрелки вниз)
        drawRoundRect(
            color = primaryTint,
            topLeft = Offset(12f * u, 16f * u),
            size = Size(68f * u, 64f * u),
            cornerRadius = CornerRadius(15f * u, 15f * u),
            style = cardStroke
        )

        val questionArc = Path().apply {
            moveTo(37.5f * u, 37.5f * u)
            cubicTo(37.5f * u, 29.8f * u, 54.5f * u, 29.8f * u, 54.5f * u, 38.6f * u)
            cubicTo(54.5f * u, 44.6f * u, 46.0f * u, 45.8f * u, 46.0f * u, 52.4f * u)
        }
        drawPath(path = questionArc, color = primaryTint, style = cardStroke)
        drawCircle(
            color = primaryTint,
            radius = 2.5f * u,
            center = Offset(46f * u, 61.8f * u)
        )

        // Круглый значок отсутствия ("×") в правом верхнем углу карточки
        drawCircle(
            color = accentTint,
            radius = 12.5f * u,
            center = Offset(77f * u, 20f * u)
        )
        val crossStroke = 2.5f * u
        drawLine(primaryTint, Offset(72.5f * u, 15.5f * u), Offset(81.5f * u, 24.5f * u), crossStroke, StrokeCap.Round)
        drawLine(primaryTint, Offset(81.5f * u, 15.5f * u), Offset(72.5f * u, 24.5f * u), crossStroke, StrokeCap.Round)
    }
}

// Иконка "+" внутри круга 70.dp
@Composable
fun SharyPlusIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.CenterCircleIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val strokeWidth = 2.2f * u
        drawLine(tint, Offset(12f * u, 5.2f * u), Offset(12f * u, 18.8f * u), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(5.2f * u, 12f * u), Offset(18.8f * u, 12f * u), strokeWidth, StrokeCap.Round)
    }
}

// Иконка Воспроизведения / Паузы внутри центрального круга 70.dp на вкладке 2 (Комната)
@Composable
fun SharyPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.CenterCircleIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        if (isPlaying) {
            val barWidth = 3.2f * u
            val barHeight = 13.2f * u
            val radius = CornerRadius(1.5f * u, 1.5f * u)
            drawRoundRect(tint, Offset(7.2f * u, 5.4f * u), Size(barWidth, barHeight), radius)
            drawRoundRect(tint, Offset(13.6f * u, 5.4f * u), Size(barWidth, barHeight), radius)
        } else {
            val playTriangle = Path().apply {
                moveTo(8.3f * u, 5.8f * u)
                lineTo(18.5f * u, 12.0f * u)
                lineTo(8.3f * u, 18.2f * u)
                close()
            }
            drawPath(path = playTriangle, color = tint, style = Fill)
            drawPath(
                path = playTriangle,
                color = tint,
                style = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

// Механика 1: Перемешивание (Shuffle)
@Composable
fun SharyShuffleIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.RoomTransportIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.85f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val topToBottom = Path().apply {
            moveTo(3.5f * u, 7.2f * u)
            cubicTo(9.2f * u, 7.2f * u, 11.6f * u, 16.8f * u, 19.4f * u, 16.8f * u)
        }
        drawPath(path = topToBottom, color = tint, style = stroke)

        val bottomArrowHead = Path().apply {
            moveTo(16.9f * u, 14.4f * u)
            lineTo(19.8f * u, 16.8f * u)
            lineTo(16.9f * u, 19.2f * u)
        }
        drawPath(path = bottomArrowHead, color = tint, style = stroke)

        val bottomToTop = Path().apply {
            moveTo(3.5f * u, 16.8f * u)
            cubicTo(9.2f * u, 16.8f * u, 11.6f * u, 7.2f * u, 19.4f * u, 7.2f * u)
        }
        drawPath(path = bottomToTop, color = tint, style = stroke)

        val topArrowHead = Path().apply {
            moveTo(16.9f * u, 4.8f * u)
            lineTo(19.8f * u, 7.2f * u)
            lineTo(16.9f * u, 9.6f * u)
        }
        drawPath(path = topArrowHead, color = tint, style = stroke)
    }
}

// Механика 2: Предыдущий трек |◀
@Composable
fun SharySkipPreviousIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.RoomTransportIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        drawRoundRect(
            color = tint,
            topLeft = Offset(5.0f * u, 5.6f * u),
            size = Size(2.2f * u, 12.8f * u),
            cornerRadius = CornerRadius(1.1f * u, 1.1f * u)
        )
        val triangle = Path().apply {
            moveTo(18.4f * u, 6.2f * u)
            lineTo(9.4f * u, 12.0f * u)
            lineTo(18.4f * u, 17.8f * u)
            close()
        }
        drawPath(path = triangle, color = tint, style = Fill)
        drawPath(path = triangle, color = tint, style = Stroke(width = 1.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Механика 3: Следующий трек ▶|
@Composable
fun SharySkipNextIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.RoomTransportIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val triangle = Path().apply {
            moveTo(5.6f * u, 6.2f * u)
            lineTo(14.6f * u, 12.0f * u)
            lineTo(5.6f * u, 17.8f * u)
            close()
        }
        drawPath(path = triangle, color = tint, style = Fill)
        drawPath(path = triangle, color = tint, style = Stroke(width = 1.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawRoundRect(
            color = tint,
            topLeft = Offset(16.8f * u, 5.6f * u),
            size = Size(2.2f * u, 12.8f * u),
            cornerRadius = CornerRadius(1.1f * u, 1.1f * u)
        )
    }
}

// Механика 4: Повтор (Repeat)
@Composable
fun SharyRepeatIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.RoomTransportIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.85f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val topLoop = Path().apply {
            moveTo(4.6f * u, 11.2f * u)
            lineTo(4.6f * u, 8.8f * u)
            cubicTo(4.6f * u, 7.4f * u, 5.4f * u, 6.6f * u, 6.8f * u, 6.6f * u)
            lineTo(18.8f * u, 6.6f * u)
        }
        drawPath(path = topLoop, color = tint, style = stroke)

        val topArrow = Path().apply {
            moveTo(16.4f * u, 4.3f * u)
            lineTo(19.2f * u, 6.6f * u)
            lineTo(16.4f * u, 8.9f * u)
        }
        drawPath(path = topArrow, color = tint, style = stroke)

        val bottomLoop = Path().apply {
            moveTo(19.4f * u, 12.8f * u)
            lineTo(19.4f * u, 15.2f * u)
            cubicTo(19.4f * u, 16.6f * u, 18.6f * u, 17.4f * u, 17.2f * u, 17.4f * u)
            lineTo(5.2f * u, 17.4f * u)
        }
        drawPath(path = bottomLoop, color = tint, style = stroke)

        val bottomArrow = Path().apply {
            moveTo(7.6f * u, 15.1f * u)
            lineTo(4.8f * u, 17.4f * u)
            lineTo(7.6f * u, 19.7f * u)
        }
        drawPath(path = bottomArrow, color = tint, style = stroke)
    }
}

// Анимация загрузки при создании/подключении к комнате:
// логотип SHARY посередине, где все 21 полоски плавно меняют цвет снизу вверх,
// после чего снова заполняются снизу вверх уже противоположным цветом (0 рекомпозиций!)
@Composable
fun SharyAnimatedLoadingLogo(
    lightColor: Color,
    darkColor: Color,
    modifier: Modifier = Modifier.size(96.dp)
) {
    val cycleProgress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    // Полный двойной цикл (проход 1 снизу вверх + проход 2 снизу вверх другим цветом) ~ 2.0 сек
                    cycleProgress.floatValue = (cycleProgress.floatValue + dt * 1.0f) % 2f
                }
                lastNanos = frameNanos
            }
        }
    }

    Canvas(modifier = modifier) {
        val cp = cycleProgress.floatValue
        val isSecondPass = cp >= 1f
        val localT = if (isSecondPass) (cp - 1f) else cp

        val baseColor = if (isSecondPass) lightColor else darkColor
        val risingColor = if (isSecondPass) darkColor else lightColor

        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        val scale = (s * 0.88f) / 0.70f
        val barWidth = 0.041f * scale
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Граница волны поднимается строго снизу вверх (от y = 0.88 до y = 0.17 в координатах логотипа)
        val scanNormY = 0.88f - localT * (0.88f - 0.17f)
        val scanPixelY = oy + s * 0.5f + (scanNormY - 0.52465f) * scale

        for (bar in SHARY_LOGO_BARS) {
            val xc = ox + s * 0.5f + (bar.cx - 0.49635f) * scale
            val yt = oy + s * 0.5f + (bar.yTop - 0.52465f) * scale
            val yb = oy + s * 0.5f + (bar.yBottom - 0.52465f) * scale
            val barHeight = (yb - yt).coerceAtLeast(barWidth)

            // Базовый цвет полоски
            drawRoundRect(
                color = baseColor,
                topLeft = Offset(xc - barWidth / 2f, yt),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )

            // Заполнение полоски снизу вверх новым цветом по мере подъёма линии волны
            if (scanPixelY < yb) {
                clipRect(
                    left = xc - barWidth,
                    top = scanPixelY.coerceAtLeast(yt - 1f),
                    right = xc + barWidth,
                    bottom = yb + 1f
                ) {
                    drawRoundRect(
                        color = risingColor,
                        topLeft = Offset(xc - barWidth / 2f, yt),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius
                    )
                }
            }
        }
    }
}

// Значок для кнопки «Войти в комнату»: человечек спереди, человечек сзади и плюсик сверху справа
@Composable
fun SharyJoinRoomPersonPlusIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.8f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Человечек сзади (чуть левее/глубже)
        val backHead = Path().apply {
            addArc(
                oval = Rect(2.6f * u, 6.2f * u, 7.6f * u, 11.2f * u),
                startAngleDegrees = 105f,
                sweepAngleDegrees = 235f
            )
        }
        drawPath(path = backHead, color = tint.copy(alpha = 0.78f), style = stroke)

        val backShoulders = Path().apply {
            moveTo(1.6f * u, 19.0f * u)
            cubicTo(2.0f * u, 15.6f * u, 3.6f * u, 14.2f * u, 5.8f * u, 14.0f * u)
        }
        drawPath(path = backShoulders, color = tint.copy(alpha = 0.78f), style = stroke)

        // Основной человечек спереди
        drawCircle(
            color = tint,
            radius = 3.1f * u,
            center = Offset(11.0f * u, 9.0f * u),
            style = stroke
        )
        val frontShoulders = Path().apply {
            moveTo(5.4f * u, 19.6f * u)
            cubicTo(5.9f * u, 15.4f * u, 8.1f * u, 14.1f * u, 11.0f * u, 14.1f * u)
            cubicTo(13.9f * u, 14.1f * u, 16.1f * u, 15.4f * u, 16.6f * u, 19.6f * u)
        }
        drawPath(path = frontShoulders, color = tint, style = stroke)

        // Значок "+" сверху справа
        val plusStroke = 1.9f * u
        drawLine(
            color = tint,
            start = Offset(19.2f * u, 3.6f * u),
            end = Offset(19.2f * u, 9.2f * u),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(16.4f * u, 6.4f * u),
            end = Offset(22.0f * u, 6.4f * u),
            strokeWidth = plusStroke,
            cap = StrokeCap.Round
        )
    }
}

// Значок выхода из комнаты слева сверху ([→) как на фото комнаты
@Composable
fun SharyExitRoomIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(SharyDimens.TopBarIconSize)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.95f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val doorBracket = Path().apply {
            moveTo(13.6f * u, 7.2f * u)
            lineTo(13.6f * u, 5.8f * u)
            cubicTo(13.6f * u, 4.6f * u, 12.8f * u, 3.8f * u, 11.6f * u, 3.8f * u)
            lineTo(6.2f * u, 3.8f * u)
            cubicTo(5.0f * u, 3.8f * u, 4.2f * u, 4.6f * u, 4.2f * u, 5.8f * u)
            lineTo(4.2f * u, 18.2f * u)
            cubicTo(4.2f * u, 19.4f * u, 5.0f * u, 20.2f * u, 6.2f * u, 20.2f * u)
            lineTo(11.6f * u, 20.2f * u)
            cubicTo(12.8f * u, 20.2f * u, 13.6f * u, 19.4f * u, 13.6f * u, 18.2f * u)
            lineTo(13.6f * u, 16.8f * u)
        }
        drawPath(path = doorBracket, color = tint, style = stroke)

        drawLine(
            color = tint,
            start = Offset(9.2f * u, 12.0f * u),
            end = Offset(20.0f * u, 12.0f * u),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        val arrowHead = Path().apply {
            moveTo(16.6f * u, 8.6f * u)
            lineTo(20.2f * u, 12.0f * u)
            lineTo(16.6f * u, 15.4f * u)
        }
        drawPath(path = arrowHead, color = tint, style = stroke)
    }
}

// Значок копирования кода комнаты слева от #CODE (два скруглённых квадрата со значком внутри как на фото)
@Composable
fun SharyCopyRoomCodeIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.8f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val backCard = Path().apply {
            moveTo(8.5f * u, 6.6f * u)
            lineTo(8.5f * u, 5.4f * u)
            cubicTo(8.5f * u, 4.2f * u, 9.3f * u, 3.4f * u, 10.5f * u, 3.4f * u)
            lineTo(18.2f * u, 3.4f * u)
            cubicTo(19.4f * u, 3.4f * u, 20.2f * u, 4.2f * u, 20.2f * u, 5.4f * u)
            lineTo(20.2f * u, 13.2f * u)
            cubicTo(20.2f * u, 14.4f * u, 19.4f * u, 15.2f * u, 18.2f * u, 15.2f * u)
            lineTo(17.0f * u, 15.2f * u)
        }
        drawPath(path = backCard, color = tint, style = stroke)

        drawRoundRect(
            color = tint,
            topLeft = Offset(3.8f * u, 7.6f * u),
            size = Size(12.6f * u, 12.6f * u),
            cornerRadius = CornerRadius(2.8f * u, 2.8f * u),
            style = stroke
        )

        val clockHands = Path().apply {
            moveTo(10.1f * u, 11.2f * u)
            lineTo(10.1f * u, 14.2f * u)
            lineTo(12.3f * u, 15.4f * u)
        }
        drawPath(path = clockHands, color = tint, style = stroke)
    }
}

// Значок «Поделиться кодом комнаты» справа от #CODE
@Composable
fun SharyShareNodesIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(21.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val strokeWidth = 1.85f * u
        val nodeStroke = Stroke(width = strokeWidth)

        val cLeft = Offset(6.2f * u, 12.0f * u)
        val cTopRight = Offset(17.6f * u, 5.8f * u)
        val cBottomRight = Offset(17.6f * u, 18.2f * u)
        val r = 2.6f * u

        drawLine(
            color = tint,
            start = Offset(8.5f * u, 10.7f * u),
            end = Offset(15.3f * u, 7.1f * u),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(8.5f * u, 13.3f * u),
            end = Offset(15.3f * u, 16.9f * u),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawCircle(color = tint, radius = r, center = cLeft, style = nodeStroke)
        drawCircle(color = tint, radius = r, center = cTopRight, style = nodeStroke)
        drawCircle(color = tint, radius = r, center = cBottomRight, style = nodeStroke)
    }
}

// Белый значок чата для правого верхнего уголка (64.dp) в комнате
@Composable
fun SharyChatBubbleIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(21.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.95f * u,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val bubble = Path().apply {
            moveTo(5.6f * u, 4.6f * u)
            lineTo(18.4f * u, 4.6f * u)
            cubicTo(20.0f * u, 4.6f * u, 21.0f * u, 5.6f * u, 21.0f * u, 7.2f * u)
            lineTo(21.0f * u, 14.4f * u)
            cubicTo(21.0f * u, 16.0f * u, 20.0f * u, 17.0f * u, 18.4f * u, 17.0f * u)
            lineTo(10.6f * u, 17.0f * u)
            lineTo(6.2f * u, 20.4f * u)
            lineTo(6.8f * u, 17.0f * u)
            lineTo(5.6f * u, 17.0f * u)
            cubicTo(4.0f * u, 17.0f * u, 3.0f * u, 16.0f * u, 3.0f * u, 14.4f * u)
            lineTo(3.0f * u, 7.2f * u)
            cubicTo(3.0f * u, 5.6f * u, 4.0f * u, 4.6f * u, 5.6f * u, 4.6f * u)
            close()
        }
        drawPath(path = bubble, color = tint, style = stroke)

        drawLine(tint, Offset(7.8f * u, 9.4f * u), Offset(16.2f * u, 9.4f * u), 1.75f * u, StrokeCap.Round)
        drawLine(tint, Offset(7.8f * u, 12.8f * u), Offset(13.4f * u, 12.8f * u), 1.75f * u, StrokeCap.Round)
    }
}

// Значок скачивания песни комнаты в свою библиотеку (справа от 0:15 | 0:55)
@Composable
fun SharyDownloadTrackIcon(
    isAlreadySaved: Boolean,
    tint: Color,
    modifier: Modifier = Modifier.size(18.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val sw = 2.0f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        if (isAlreadySaved) {
            val check = Path().apply {
                moveTo(5.0f * u, 12.6f * u)
                lineTo(10.0f * u, 17.6f * u)
                lineTo(19.2f * u, 7.4f * u)
            }
            drawPath(path = check, color = tint, style = stroke)
        } else {
            drawLine(
                color = tint,
                start = Offset(12.0f * u, 4.0f * u),
                end = Offset(12.0f * u, 15.8f * u),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            val arrow = Path().apply {
                moveTo(7.6f * u, 11.8f * u)
                lineTo(12.0f * u, 16.4f * u)
                lineTo(16.4f * u, 11.8f * u)
            }
            drawPath(path = arrow, color = tint, style = stroke)

            drawLine(
                color = tint,
                start = Offset(5.4f * u, 19.8f * u),
                end = Offset(18.6f * u, 19.8f * u),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }
}

// Значок отправки сообщения в чате комнаты
@Composable
fun SharySendMessageIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val plane = Path().apply {
            moveTo(3.4f * u, 11.4f * u)
            lineTo(20.4f * u, 4.2f * u)
            lineTo(13.8f * u, 20.6f * u)
            lineTo(10.8f * u, 13.6f * u)
            close()
        }
        drawPath(
            path = plane,
            color = tint,
            style = Stroke(
                width = 1.85f * u,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawLine(
            color = tint,
            start = Offset(10.8f * u, 13.6f * u),
            end = Offset(20.0f * u, 4.6f * u),
            strokeWidth = 1.6f * u,
            cap = StrokeCap.Round
        )
    }
}

// Стандартная серая аватарка-заглушка приложения (статичный серый логотип SHARY, если у пользователя нет своего фото)
@Composable
fun SharyDefaultGrayAvatarIcon(
    backgroundTint: Color,
    silhouetteTint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(color = backgroundTint, radius = r, center = Offset(cx, cy))

        val logoBox = size.minDimension * 0.62f
        val ox = cx - logoBox / 2f
        val oy = cy - logoBox / 2f
        val scale = (logoBox * 0.88f) / 0.70f
        val barWidth = 0.041f * scale
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        val primaryBarColor = silhouetteTint.copy(alpha = (silhouetteTint.alpha * 0.72f).coerceIn(0.25f, 1f))
        val secondaryBarColor = silhouetteTint

        for (bar in SHARY_LOGO_BARS) {
            val xc = ox + logoBox * 0.5f + (bar.cx - 0.49635f) * scale
            val yt = oy + logoBox * 0.5f + (bar.yTop - 0.52465f) * scale
            val yb = oy + logoBox * 0.5f + (bar.yBottom - 0.52465f) * scale
            drawRoundRect(
                color = if (bar.isSecondary) secondaryBarColor else primaryBarColor,
                topLeft = Offset(xc - barWidth / 2f, yt),
                size = Size(barWidth, (yb - yt).coerceAtLeast(barWidth)),
                cornerRadius = radius
            )
        }
    }
}

// Значок Wi-Fi для вкладки «Настройки»
@Composable
fun SharyWifiIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = -138f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = Offset(2.0f * u, 4.2f * u),
            size = Size(20.0f * u, 20.0f * u),
            style = stroke
        )
        drawArc(
            color = tint,
            startAngle = -135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(5.6f * u, 8.6f * u),
            size = Size(12.8f * u, 12.8f * u),
            style = stroke
        )
        drawCircle(
            color = tint,
            radius = 1.8f * u,
            center = Offset(12.0f * u, 18.2f * u)
        )
    }
}

// Значок щита безопасности (Только безопасные MP3) для вкладки «Настройки»
@Composable
fun SharySafeMp3ShieldIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.85f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shield = Path().apply {
            moveTo(12.0f * u, 3.0f * u)
            lineTo(19.2f * u, 6.0f * u)
            lineTo(19.2f * u, 11.8f * u)
            cubicTo(19.2f * u, 16.4f * u, 16.0f * u, 19.6f * u, 12.0f * u, 21.2f * u)
            cubicTo(8.0f * u, 19.6f * u, 4.8f * u, 16.4f * u, 4.8f * u, 11.8f * u)
            lineTo(4.8f * u, 6.0f * u)
            close()
        }
        drawPath(path = shield, color = tint, style = stroke)

        val check = Path().apply {
            moveTo(9.0f * u, 12.2f * u)
            lineTo(11.1f * u, 14.3f * u)
            lineTo(15.2f * u, 9.8f * u)
        }
        drawPath(path = check, color = tint, style = stroke)
    }
}

// Значок ползунков эквалайзера для вкладки «Настройки»
@Composable
fun SharyEqualizerSlidersIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val sw = 1.85f * u
        val xs = floatArrayOf(6.0f * u, 12.0f * u, 18.0f * u)
        val knobYs = floatArrayOf(9.0f * u, 15.2f * u, 7.6f * u)
        val r = 2.2f * u

        for (i in 0..2) {
            val x = xs[i]
            val ky = knobYs[i]
            drawLine(tint, Offset(x, 4.2f * u), Offset(x, (ky - r).coerceAtLeast(4.2f * u)), sw, StrokeCap.Round)
            drawLine(tint, Offset(x, (ky + r).coerceAtMost(19.8f * u)), Offset(x, 19.8f * u), sw, StrokeCap.Round)
            drawCircle(color = tint, radius = r, center = Offset(x, ky), style = Stroke(width = sw))
        }
    }
}

// Значок таймера сна для вкладки «Настройки»
@Composable
fun SharySleepTimerIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.85f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(
            color = tint,
            radius = 8.2f * u,
            center = Offset(12.0f * u, 12.6f * u),
            style = stroke
        )
        drawLine(tint, Offset(9.5f * u, 2.8f * u), Offset(14.5f * u, 2.8f * u), 1.85f * u, StrokeCap.Round)
        val hand = Path().apply {
            moveTo(12.0f * u, 8.2f * u)
            lineTo(12.0f * u, 12.6f * u)
            lineTo(15.2f * u, 14.4f * u)
        }
        drawPath(path = hand, color = tint, style = stroke)
    }
}

// Двухцветный значок ноты в центре виниловой пластинки комнаты (когда у трека нет обложки)
@Composable
fun SharyVinylCenterNoteIcon(
    lightTint: Color,
    darkTint: Color,
    modifier: Modifier = Modifier.size(30.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        // Тёмная тень ноты справа + светлая Milk-нота слева (как на референсе)
        drawCircle(
            color = darkTint,
            radius = 3.8f * u,
            center = Offset(10.8f * u, 16.4f * u)
        )
        drawCircle(
            color = lightTint,
            radius = 3.6f * u,
            center = Offset(9.0f * u, 16.6f * u)
        )
        val stem = Path().apply {
            moveTo(12.2f * u, 16.4f * u)
            lineTo(12.2f * u, 5.2f * u)
            lineTo(18.0f * u, 5.2f * u)
            lineTo(18.0f * u, 8.2f * u)
            lineTo(14.4f * u, 8.2f * u)
            lineTo(14.4f * u, 16.4f * u)
            close()
        }
        drawPath(path = stem, color = lightTint, style = Fill)
    }
}

// Значок глаза / перечёркнутого глаза в правой области ввода пароля:
// - crossedOut = false (глаз): пароль скрыт точками, нажатие показывает символы;
// - crossedOut = true (перечёркнутый глаз): символы видны, нажатие скрывает пароль за точками.
@Composable
fun SharyPasswordEyeIcon(
    crossedOut: Boolean,
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val u = size.minDimension / 24f
        val strokeWidth = 1.85f * u
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val eyeContour = Path().apply {
            moveTo(2.4f * u, 12.0f * u)
            cubicTo(
                6.2f * u, 5.8f * u,
                17.8f * u, 5.8f * u,
                21.6f * u, 12.0f * u
            )
            cubicTo(
                17.8f * u, 18.2f * u,
                6.2f * u, 18.2f * u,
                2.4f * u, 12.0f * u
            )
            close()
        }
        drawPath(path = eyeContour, color = tint, style = stroke)

        drawCircle(
            color = tint,
            radius = 3.0f * u,
            center = Offset(12.0f * u, 12.0f * u),
            style = stroke
        )

        if (crossedOut) {
            drawLine(
                color = tint,
                start = Offset(4.0f * u, 4.0f * u),
                end = Offset(20.0f * u, 20.0f * u),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}


