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

    /**
     * 전부 지연 생성이다. {@code init} 은 Application.onCreate 에서 불리므로 여기서 만드는
     * 것은 모두 <b>콜드 스타트의 메인 스레드</b>를 잡아먹는다 — 실측으로 TokenStore 200ms,
     * OkHttp 260ms, FusedLocation 75ms, 연결 감시 45ms 였다(총 0.58초). 어느 것도 첫 화면을
     * 그리는 데 필요하지 않고, 처음 쓰는 시점은 대개 코루틴(워커 스레드)이라 그때 만드는 게
     * 낫다. 첫 접근이 동시에 일어나도 {@code by lazy} 가 한 번만 만든다.
     */
    private var appContext: Context? = null

    private fun context(): Context =
        appContext ?: error("AppServices.init 을 먼저 불러야 한다")

    val tokenStore: TokenStore by lazy { TokenStore(context()) }

    val api: WoojuinApi by lazy { WoojuinApi(tokenStore) }

    val auth: AuthRepository by lazy { RemoteAuthRepository(api, tokenStore) }

    val place: PlaceRepository by lazy { RemotePlaceRepository(context(), api, workspaces) }

    val connectivity: ConnectivityMonitor by lazy { ConnectivityMonitor(context()) }

    /**
     * 음성 저장·검색은 음성 인식기를 공유한다. 인식기 소유자가 fake 쪽
     * Repositories 라서(바인딩을 데워 재사용한다) 여기서 만들지 않고 주입받는다.
     */
    private val workspaces: WorkspaceResolver by lazy { WorkspaceResolver(api) }

    fun voiceCapture(speech: SpeechSource): VoiceCaptureRepository =
        RemoteVoiceCaptureRepository(api, workspaces, speech)

    fun search(speech: SpeechSource): SearchRepository =
        RemoteSearchRepository(api, workspaces, speech)

    /** Compose Preview 는 init 을 거치지 않는다 — 그때는 fake 로 폴백한다 */
    val initialized: Boolean
        get() = appContext != null

    /** 컨텍스트만 받아 둔다 — 실제 생성은 각 서비스를 처음 쓸 때다(위 주석 참고). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
