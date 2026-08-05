package com.ssafy.woojuin.data

import android.content.Context
import com.ssafy.woojuin.data.auth.TokenStore
import com.ssafy.woojuin.data.remote.RemoteAuthRepository
import com.ssafy.woojuin.data.remote.RemotePlaceRepository
import com.ssafy.woojuin.data.remote.WoojuinApi
import com.ssafy.woojuin.domain.repository.AuthRepository
import com.ssafy.woojuin.domain.repository.PlaceRepository

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

    lateinit var place: PlaceRepository
        private set

    lateinit var connectivity: ConnectivityMonitor
        private set

    /** Compose Preview 는 init 을 거치지 않는다 — 그때는 fake 로 폴백한다 */
    val initialized: Boolean
        get() = ::tokenStore.isInitialized

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        api = WoojuinApi(tokenStore)
        auth = RemoteAuthRepository(api, tokenStore)
        place = RemotePlaceRepository(context.applicationContext, api)
        connectivity = ConnectivityMonitor(context.applicationContext)
    }
}
