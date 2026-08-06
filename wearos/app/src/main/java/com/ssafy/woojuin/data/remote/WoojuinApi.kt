package com.ssafy.woojuin.data.remote

import com.ssafy.woojuin.BuildConfig
import com.ssafy.woojuin.data.auth.TokenStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** 서버가 세션을 거부했다 — 토큰은 이미 지워졌고, 화면은 링크로 돌아가야 한다 */
class AuthRequiredException : Exception("세션이 거부되었습니다")

/**
 * 우주인 REST 클라이언트.
 *
 * UA 를 직접 정한다 — 기기 목록의 이름("Wear OS 워치")이 이 문자열에서 나온다(-459).
 *
 * ── 토큰 폐기 계약 (S15P11C105-455 와 동일) ─────────────────────────────
 * 통신 실패·타임아웃·5xx 에는 토큰을 지우지 않는다. 워치는 블루투스가 끊기는 게
 * 일상이라, 여기서 지우면 끊길 때마다 재링크를 강요한다. **서버가 거부한 상태
 * 코드(400·401·403)를 받은 때만** 지운다 — refresh 까지 거부됐다는 뜻이므로
 * 세션이 정말 죽은 것이다(웹 기기 관리에서 해제했거나 만료).
 */
class WoojuinApi(private val tokenStore: TokenStore) {

    companion object {
        const val USER_AGENT = "Woojuin-WearOS/1.0"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val REFRESH_REJECTED_CODES = setOf(400, 401, 403)
    }

    private val baseUrl = BuildConfig.API_BASE_URL

    /**
     * 지연 생성이다 — 클라이언트를 만드는 데 실측 260ms 가 들었고(OkHttp 클래스 로딩),
     * 그게 콜드 스타트의 메인 스레드에 그대로 얹혀 있었다. 첫 요청은 항상 워커
     * 스레드에서 나가므로 비용을 그쪽으로 넘긴다.
     */
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("User-Agent", USER_AGENT).build(),
                )
            }
            .build()
    }

    private val refreshLock = Any()

    // ── 비인증 호출 (링크 코드) ──────────────────────────────────────────

    fun startDeviceLink(): JSONObject =
        execute(post("/auth/device-link", JSONObject())).let { body ->
            body.getJSONObject("data")
        }

    /** 폴링 한 번. 만료·소비된 코드는 400 이 오므로 호출자가 상태 코드로 구분한다 */
    fun pollDeviceLink(code: String): PollOutcome {
        val request = post("/auth/device-link/poll", JSONObject().put("code", code))
        client.newCall(request).execute().use { response ->
            if (response.code == 400) return PollOutcome.Expired
            val data = parseOrThrow(response).getJSONObject("data")
            return if (data.getString("status") == "APPROVED") {
                PollOutcome.Approved(data.getString("accessToken"), data.getString("refreshToken"))
            } else {
                PollOutcome.Pending
            }
        }
    }

    sealed interface PollOutcome {
        data object Pending : PollOutcome
        data object Expired : PollOutcome
        data class Approved(val accessToken: String, val refreshToken: String) : PollOutcome
    }

    // ── 인증 호출 ────────────────────────────────────────────────────────

    /**
     * Authorization 을 붙여 부르고, 401 이면 refresh 후 딱 한 번 다시 시도한다.
     * refresh 까지 거부되면 토큰을 지우고 [AuthRequiredException] — 그 외 실패는
     * IOException 으로 흘려보낸다(토큰 유지, 화면은 재시도 안내).
     */
    fun authorized(path: String, body: JSONObject): JSONObject =
        authorizedCall { access -> post(path, body, access) }

    fun authorizedGet(path: String): JSONObject =
        authorizedCall { access ->
            Request.Builder().url(baseUrl + path)
                .header("Authorization", "Bearer $access").get().build()
        }

    /** 본문 없는 DELETE. 401 재시도·토큰 폐기 계약은 다른 인증 호출과 같다. */
    fun authorizedDelete(path: String): JSONObject =
        authorizedCall { access ->
            Request.Builder().url(baseUrl + path)
                .header("Authorization", "Bearer $access").delete().build()
        }

    /**
     * 파일 하나를 multipart 로 올린다(오디오 받아쓰기). 401 재시도·토큰 폐기 계약은 다른
     * 인증 호출과 같다.
     *
     * <p>바이트를 그대로 실어 보낸다 — 워치가 만드는 오디오는 한마디에 100~200KB 라
     * 스트리밍할 이유가 없고, 재시도(401 후 1회) 때 같은 본문을 다시 써야 한다.
     */
    fun authorizedUpload(
        path: String,
        fieldName: String,
        filename: String,
        contentType: String,
        bytes: ByteArray,
    ): JSONObject =
        authorizedCall(uploadClient) { access ->
            val body = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    fieldName,
                    filename,
                    bytes.toRequestBody(contentType.toMediaType()),
                )
                .build()
            Request.Builder().url(baseUrl + path)
                .header("Authorization", "Bearer $access").post(body).build()
        }

    private fun authorizedCall(
        http: OkHttpClient = client,
        build: (String) -> Request,
    ): JSONObject {
        val access = tokenStore.accessTokenBlocking() ?: throw AuthRequiredException()
        val first = call(build(access), http)
        if (first.first != 401) return parseBody(first)

        refreshOrThrow()
        val retryAccess = tokenStore.accessTokenBlocking() ?: throw AuthRequiredException()
        val second = call(build(retryAccess), http)
        if (second.first == 401) {
            // 새 access 조차 거부 — 그 사이 기기 해제(폐기 목록)된 경우다
            runBlockingClear()
            throw AuthRequiredException()
        }
        return parseBody(second)
    }

    /** refresh 성공 시 토큰 갱신. 거부(400·401·403)면 폐기 후 예외, 그 외엔 IOException */
    private fun refreshOrThrow() {
        synchronized(refreshLock) {
            val refresh = tokenStore.refreshTokenBlocking() ?: throw AuthRequiredException()
            val request = post("/auth/token/refresh", JSONObject().put("refreshToken", refresh))
            client.newCall(request).execute().use { response ->
                if (response.code in REFRESH_REJECTED_CODES) {
                    runBlockingClear()
                    throw AuthRequiredException()
                }
                val data = parseOrThrow(response).getJSONObject("data")
                kotlinx.coroutines.runBlocking {
                    tokenStore.save(data.getString("accessToken"), data.getString("refreshToken"))
                }
            }
        }
    }

    private fun runBlockingClear() = kotlinx.coroutines.runBlocking { tokenStore.clear() }

    // ── 공통 ────────────────────────────────────────────────────────────

    private fun post(path: String, body: JSONObject, accessToken: String? = null): Request =
        Request.Builder()
            .url(baseUrl + path)
            .post(body.toString().toRequestBody(JSON))
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .build()

    private fun call(request: Request, http: OkHttpClient = client): Pair<Int, String> =
        http.newCall(request).execute().use { response ->
            response.code to (response.body?.string() ?: "")
        }

    /**
     * 오디오 업로드 전용 — 읽기 타임아웃이 길다. 연결 풀·디스패처는 [client] 와 공유한다.
     *
     * 일반 호출(10초)로는 부족하다. 서버가 받아쓰기(whisper)를 부르는 시간이 응답에 더해지고,
     * 무엇보다 **첫 호출이 비싸다** — dev 실기기 실측에서 1차 요청이 10.1초에 타임아웃했고
     * 2차는 1.5초에 끝났다(워치→서버 TLS 수립과 서버→프록시 첫 연결을 1차가 다 낸다).
     * 시연에서 첫 시도가 실패하면 그게 곧 실패이므로 넉넉히 잡는다.
     */
    private val uploadClient by lazy {
        client.newBuilder()
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun parseBody(result: Pair<Int, String>): JSONObject {
        val (code, body) = result
        if (code !in 200..299) throw IOException("HTTP $code: ${body.take(200)}")
        return JSONObject(body)
    }

    private fun execute(request: Request): JSONObject =
        client.newCall(request).execute().use { parseOrThrow(it) }

    private fun parseOrThrow(response: Response): JSONObject {
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${body.take(200)}")
        return JSONObject(body)
    }
}
