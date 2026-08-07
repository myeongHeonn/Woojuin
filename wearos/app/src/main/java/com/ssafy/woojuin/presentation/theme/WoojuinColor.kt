package com.ssafy.woojuin.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * 우주인 브랜드 디자인 토큰 — **웹(frontend/src/styles/theme.css)의 값을 그대로 쓴다.**
 * 두 제품이 한 가족으로 보이려면 표면·글자·강조가 같은 값이어야 한다. 예전에는 워치가
 * 웹의 어두운 변종을 따로 갖고 있어서, 나란히 놓으면 다른 앱처럼 보였다.
 *
 * <p><b>배경만 예외다.</b> 웹의 스테이지는 `#0e1017` 이지만 워치는 순수 검정을 쓴다 —
 * OLED 는 검은 화소를 아예 끄므로 전력을 덜 쓰고(항상 화면이 켜져 있는 기기다), 둥근
 * 베젤과 화면 경계가 사라져 화면이 더 깊어 보인다. Wear 의 기본 배경도 검정이다.
 */
object WoojuinColor {

    // ── 배경·표면 ────────────────────────────────────────────────────────────
    /** 배경. 웹과 다른 유일한 값(위 주석의 이유). */
    val SpaceBlack = Color(0xFF000000)

    /** 웹 --color-sidebar. 배경보다 한 겹 위(스크림·시트) */
    val SidebarBlack = Color(0xFF171923)

    /** 웹 --color-surface. 기본 카드·행의 채움 */
    val Surface = Color(0xFF20242F)

    /** 웹 --color-surface-2. 눌린·활성 상태 */
    val SurfaceActive = Color(0xFF2A2E3A)

    /** 웹 --color-surface-3. 표면 위의 표면(아이콘 원, 강조 버튼) */
    val SurfaceRaised = Color(0xFF343947)

    /** 웹 --color-border-soft. 헤어라인 — 워치에서 테두리는 이 값만 쓴다 */
    val Border = Color(0xFF24272F)

    // ── 글자 ────────────────────────────────────────────────────────────────
    /** 웹 --color-text-1 */
    val TextPrimary = Color(0xFFF0F2F6)

    /** 웹 --color-text-2 */
    val TextSecondary = Color(0xFFB0B6C3)

    /** 웹 --color-text-3 */
    val TextMuted = Color(0xFF7B8290)

    // ── 강조·별 색 (웹과 동일) ───────────────────────────────────────────────
    val AccentPurple = Color(0xFF7C6CF0)
    val AccentHover = Color(0xFF8F80F5)
    val Danger = Color(0xFFEF7A72)
    val StarLavender = Color(0xFFC9B8FF)
    val StarBlue = Color(0xFF8FB4FF)
    val StarGreen = Color(0xFFB8E6A3)
    val StarOrange = Color(0xFFF5B08A)
    val StarYellow = Color(0xFFF2D96B)
    val StarWhite = Color(0xFFF5F1E8)

    // 스파클 그라데이션 (로고 자산 원본 색)
    val SparkleCore = Color(0xFFFFF1E4)
    val SparkleTail = Color(0xFF3A71FF)

    /**
     * 기능별 피드백 색. **기능마다 색을 고정한다** — 워치는 글자를 길게 읽지 않으므로,
     * 색이 "지금 무슨 기능인지"를 말하는 두 번째 언어가 된다(웹의 별 색과 같은 값).
     */
    val VoiceAccent = AccentPurple
    val SearchAccent = StarBlue
    val SongAccent = StarYellow
    val PlaceAccent = StarGreen
}
