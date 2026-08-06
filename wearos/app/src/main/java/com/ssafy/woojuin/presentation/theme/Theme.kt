package com.ssafy.woojuin.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

private val WoojuinColorScheme = ColorScheme(
    primary = WoojuinColor.AccentPurple,
    primaryDim = WoojuinColor.AccentPurple.copy(alpha = 0.8f),
    primaryContainer = WoojuinColor.SurfaceActive,
    onPrimary = WoojuinColor.SpaceBlack,
    onPrimaryContainer = WoojuinColor.StarLavender,
    secondary = WoojuinColor.StarBlue,
    secondaryDim = WoojuinColor.StarBlue.copy(alpha = 0.8f),
    secondaryContainer = WoojuinColor.SurfaceRaised,
    onSecondary = WoojuinColor.SpaceBlack,
    onSecondaryContainer = WoojuinColor.TextPrimary,
    tertiary = WoojuinColor.StarGreen,
    tertiaryDim = WoojuinColor.StarGreen.copy(alpha = 0.8f),
    tertiaryContainer = WoojuinColor.SurfaceRaised,
    onTertiary = WoojuinColor.SpaceBlack,
    onTertiaryContainer = WoojuinColor.TextPrimary,
    surfaceContainerLow = WoojuinColor.SidebarBlack,
    surfaceContainer = WoojuinColor.Surface,
    surfaceContainerHigh = WoojuinColor.SurfaceRaised,
    onSurface = WoojuinColor.TextPrimary,
    onSurfaceVariant = WoojuinColor.TextSecondary,
    outline = WoojuinColor.Border,
    outlineVariant = WoojuinColor.Border,
    background = WoojuinColor.SpaceBlack,
    onBackground = WoojuinColor.TextPrimary,
)

/**
 * 한국어 줄바꿈 규칙.
 *
 * <p>워치의 좁은 폭에서 두 줄이 되는 문장이 흔하다. 서버에서 오는 제목·오류 문구는 우리가
 * 길이를 정하지 못하므로, 어디서 끊기는지를 규칙으로 정해 둔다.
 *
 * <ul>
 *   <li>[LineBreak.Strategy.Balanced] — 두 줄의 길이를 비슷하게 나눈다. 첫 줄만 꽉 차고
 *       둘째 줄에 두 글자만 남는 모양을 막는다
 *   <li>[LineBreak.WordBreak.Phrase] — 한중일 문장을 어절 단위로 끊는다
 * </ul>
 *
 * <p>정작 낱말이 쪼개진 진짜 원인은 로케일이었다 — [KoreanLocale] 을 함께 봐야 한다.
 */
private val KoreanLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Balanced,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/**
 * 글자의 언어를 한국어로 못박는다.
 *
 * <p>안드로이드의 줄바꿈은 **글자의 로케일**을 보고 규칙을 고른다. 기기 로케일이 영어면
 * 한글을 중국어·일본어처럼 다뤄 아무 자리에서나 끊는다 — en-US 에뮬레이터에서 "비슷한
 * 자료" / "를 찾지 못했어요" 처럼 조사가 떨어져 나갔다(ko-KR 실기기에서는 정상이었다).
 * 앱의 문구는 전부 한국어이므로 기기 설정과 무관하게 한국어 규칙을 쓰는 것이 맞다.
 */
private val KoreanLocale = LocaleList("ko")

private fun TextStyle.withKoreanLineBreak(): TextStyle =
    copy(lineBreak = KoreanLineBreak, localeList = KoreanLocale)

/** 기본 타이포그래피에 줄바꿈 규칙만 얹는다 — 글자 크기·굵기는 Wear 기본값을 그대로 쓴다. */
private val WoojuinTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.withKoreanLineBreak(),
        displayMedium = base.displayMedium.withKoreanLineBreak(),
        displaySmall = base.displaySmall.withKoreanLineBreak(),
        titleLarge = base.titleLarge.withKoreanLineBreak(),
        titleMedium = base.titleMedium.withKoreanLineBreak(),
        titleSmall = base.titleSmall.withKoreanLineBreak(),
        labelLarge = base.labelLarge.withKoreanLineBreak(),
        labelMedium = base.labelMedium.withKoreanLineBreak(),
        labelSmall = base.labelSmall.withKoreanLineBreak(),
        bodyLarge = base.bodyLarge.withKoreanLineBreak(),
        bodyMedium = base.bodyMedium.withKoreanLineBreak(),
        bodySmall = base.bodySmall.withKoreanLineBreak(),
        bodyExtraSmall = base.bodyExtraSmall.withKoreanLineBreak(),
    )
}

@Composable
fun WoojuinTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WoojuinColorScheme,
        typography = WoojuinTypography,
        content = content
    )
}
