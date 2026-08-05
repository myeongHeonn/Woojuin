package com.ssafy.woojuin.data

import android.content.Context
import com.ssafy.woojuin.data.auth.TokenStore
import com.ssafy.woojuin.data.remote.RemoteAuthRepository
import com.ssafy.woojuin.data.remote.WoojuinApi
import com.ssafy.woojuin.domain.repository.AuthRepository

/**
 * 실서버 연결 조각들의 서비스 로케이터 — fake 쪽 Repositories 와 같은 패턴.
 * 실연결이 fake 를 하나씩 대체할 때마다 여기로 옮겨 온다.
 */
object AppServices {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var api: WoojuinApi
        private set

    lateinit var auth: AuthRepository
        private set

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        api = WoojuinApi(tokenStore)
        auth = RemoteAuthRepository(api, tokenStore)
    }
}
