package com.ssafy.woojuin.data

import android.content.Context
import com.ssafy.woojuin.data.auth.TokenStore
import com.ssafy.woojuin.data.remote.RemoteAuthRepository
import com.ssafy.woojuin.data.remote.RemotePlaceRepository
import com.ssafy.woojuin.data.remote.RemoteSearchRepository
import com.ssafy.woojuin.data.remote.RemoteVoiceCaptureRepository
import com.ssafy.woojuin.data.remote.WoojuinApi
import com.ssafy.woojuin.data.remote.WorkspaceResolver
import com.ssafy.woojuin.domain.repository.AuthRepository
import com.ssafy.woojuin.domain.repository.PlaceRepository
import com.ssafy.woojuin.domain.repository.SearchRepository
import com.ssafy.woojuin.domain.repository.SpeechSource
import com.ssafy.woojuin.domain.repository.VoiceCaptureRepository

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

    /**
     * 음성 저장·검색은 음성 인식기를 공유한다. 인식기 소유자가 fake 쪽
     * Repositories 라서(바인딩을 데워 재사용한다) 여기서 만들지 않고 주입받는다.
     */
    private lateinit var workspaces: WorkspaceResolver

    fun voiceCapture(speech: SpeechSource): VoiceCaptureRepository =
        RemoteVoiceCaptureRepository(api, workspaces, speech)

    fun search(speech: SpeechSource): SearchRepository =
        RemoteSearchRepository(api, workspaces, speech)

    /** Compose Preview 는 init 을 거치지 않는다 — 그때는 fake 로 폴백한다 */
    val initialized: Boolean
        get() = ::tokenStore.isInitialized

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        api = WoojuinApi(tokenStore)
        workspaces = WorkspaceResolver(api)
        auth = RemoteAuthRepository(api, tokenStore)
        place = RemotePlaceRepository(context.applicationContext, api, workspaces)
        connectivity = ConnectivityMonitor(context.applicationContext)
    }
}
