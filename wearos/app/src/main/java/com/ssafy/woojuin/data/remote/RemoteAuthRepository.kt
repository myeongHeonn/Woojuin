package com.ssafy.woojuin.data.remote

import com.ssafy.woojuin.data.auth.TokenStore
import com.ssafy.woojuin.domain.repository.AuthRepository
import com.ssafy.woojuin.domain.repository.LinkCode
import com.ssafy.woojuin.domain.repository.LinkPollResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 링크 코드 로그인의 실서버 구현 — 통신 실패는 예외로 흘려 화면이 재시도를 안내한다 */
class RemoteAuthRepository(
    private val api: WoojuinApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    override suspend fun startLink(): LinkCode = withContext(Dispatchers.IO) {
        val data = api.startDeviceLink()
        LinkCode(data.getString("code"), data.getLong("expiresInSeconds"))
    }

    override suspend fun pollLink(code: String): LinkPollResult = withContext(Dispatchers.IO) {
        when (val outcome = api.pollDeviceLink(code)) {
            is WoojuinApi.PollOutcome.Pending -> LinkPollResult.Pending
            is WoojuinApi.PollOutcome.Expired -> LinkPollResult.Expired
            is WoojuinApi.PollOutcome.Approved -> {
                tokenStore.save(outcome.accessToken, outcome.refreshToken)
                LinkPollResult.Approved
            }
        }
    }
}
