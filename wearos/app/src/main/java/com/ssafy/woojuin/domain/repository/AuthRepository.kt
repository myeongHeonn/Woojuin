package com.ssafy.woojuin.domain.repository

import kotlinx.coroutines.flow.Flow

/** 워치 화면에 띄울 링크 코드 — 만료되면 새로 발급받는다 */
data class LinkCode(val code: String, val expiresInSeconds: Long)

/** 폴링 한 번의 결과. 통신 실패는 예외로 던진다 — 코드가 죽은 게 아니라 몰라서다 */
sealed interface LinkPollResult {
    data object Pending : LinkPollResult
    data object Approved : LinkPollResult

    /** 만료·소비된 코드 — 새 코드를 발급받아 다시 띄운다 */
    data object Expired : LinkPollResult
}

/**
 * 링크 코드 로그인 (S15P11C105-458).
 *
 * 워치는 코드를 화면에 띄우기만 하고, 승인은 웹 기기 관리 화면에서 한다.
 * 로그인 결과는 다른 기기와 동일한 세션(-459) — 웹에서 해제하면 이 워치가 끊긴다.
 */
interface AuthRepository {
    /** 토큰 보유 여부. 끊기면(서버 거부로 토큰 폐기) false 로 흐른다 — 화면이 링크로 돌아간다 */
    val isLoggedIn: Flow<Boolean>

    suspend fun startLink(): LinkCode

    suspend fun pollLink(code: String): LinkPollResult
}
