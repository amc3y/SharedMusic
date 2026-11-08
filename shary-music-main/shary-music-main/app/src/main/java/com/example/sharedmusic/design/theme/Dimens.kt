package com.example.sharedmusic.design.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SharyDimens {
    // Внешний отступ всех 3 плавающих панелей от краёв экрана
    val ScreenOuterMargin: Dp = 12.dp

    // Высота верхней панели
    val TopAreaHeight: Dp = 60.dp

    // Плавный овальный вырез сверху посередине в 1-й панели
    val TopCameraNotchBaseRadius: Dp = 28.dp
    val TopCameraNotchShoulderRadius: Dp = 14.dp
    val TopCameraNotchCenterOffsetY: Dp = (-17).dp
    const val TopCameraNotchOvalStretchX: Float = 2.65f

    val TopBarIconSize: Dp = 26.dp
    val TopBarTouchTargetSize: Dp = 44.dp

    // Зазор между верхней и средней серыми областями
    val TopToMiddleGap: Dp = 10.dp

    // Внешнее скругление углов всех 3 серых областей
    val PanelOuterRadius: Dp = 16.dp

    // Скругление элементов внутри панелей
    val PanelInnerElementRadius: Dp = 8.dp

    // Внутренний отступ контента внутри средней панели
    val PanelInnerPadding: Dp = 12.dp

    // Нижняя панель навигации и слегка опущенный центральный круг "+" / Play-Pause (70.dp) с сохранением впадины
    val BottomAreaBarHeight: Dp = 68.dp
    val AddButtonSize: Dp = 70.dp
    val BottomNotchOuterRadius: Dp = 42.dp
    val BottomNotchShoulderRadius: Dp = 12.dp
    val BottomNotchCenterOffsetY: Dp = (-8).dp
    val BottomNotchHorizontalSpan: Dp = 102.dp

    // Иконки нижней панели навигации
    val BottomNavIconSize: Dp = 26.dp
    val BottomNavTouchTargetSize: Dp = 46.dp
    val BottomNavSelectionCircleSize: Dp = 44.dp
    val BottomNavIconSpacing: Dp = 16.dp
    val CenterCircleIconSize: Dp = 28.dp

    // Полоса из 4 значков механики музыки между средней и нижней серыми областями (в активной Комнате)
    val RoomTransportBandHeight: Dp = 54.dp
    val RoomTransportIconSize: Dp = 24.dp
    val RoomTransportTouchTargetSize: Dp = 44.dp
    val RoomTransportSelectionCircleSize: Dp = 42.dp

    // Полоса кружков участников комнаты над средней серой областью
    val RoomParticipantsStripHeight: Dp = 84.dp
    val RoomParticipantAvatarSize: Dp = 54.dp
    val RoomParticipantBadgeSize: Dp = 19.dp
    val RoomMiniPlateSize: Dp = 84.dp

    // Элементы плейлиста в 1-м окне (Библиотека)
    val SubTabBarHeight: Dp = 44.dp
    val SubTabIconSize: Dp = 22.dp
    val SongCoverSize: Dp = 48.dp
    val SongActionTouchSize: Dp = 36.dp
    val SongActionIconSize: Dp = 20.dp

    // Увеличенный в 2 раза значок пустого списка (без стрелки и без текста)
    val EmptyStateIconSize: Dp = 200.dp

    // Элементы винилового проигрывателя и уголка чата/сворачивания (одинаковый размер 64.dp)
    val PlayerCornerTriangleSize: Dp = 64.dp
    val PlayerVinylMaxSize: Dp = 248.dp
    val PlayerPlayButtonSize: Dp = 58.dp
    val PlayerMiniPlateSize: Dp = 94.dp
    val PlayerMiniPlateCornerRadius: Dp = 12.dp
    val PlayerMiniPlateArchLift: Dp = 24.dp

    // Стандартный нижний отступ средней области
    val MiddleBottomInsetDefault: Dp =
        ScreenOuterMargin + BottomAreaBarHeight + 10.dp

    // Во вкладке «Комната» ДО создания комнаты (впадина и круг снизу убраны)
    val MiddleBottomInsetRoomLobby: Dp =
        ScreenOuterMargin + BottomAreaBarHeight + 10.dp

    // Во вкладке «Комната» ПОСЛЕ входа/создания (появляется полоса из 4 значков музыки и круг Play)
    val MiddleBottomInsetRoom: Dp =
        ScreenOuterMargin + BottomAreaBarHeight + RoomTransportBandHeight + 8.dp

    // Толщина полупрозрачной стеклянной обводки
    val GlassBorderWidth: Dp = 1.dp
}
