package com.ssafy.woojuin.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * 토큰 영속 저장 — 앱을 다시 켜도 로그인이 유지되는 근거 (S15P11C105-458 AC).
 *
 * 지우는 조건이 이 클래스의 존재 이유다: **서버가 거부(400·401·403)했을 때만** 지운다.
 * 통신 실패·타임아웃·5xx 에 지우면 블루투스가 잠깐 끊길 때마다 재링크를 강요하게 된다
 * — 웹이 S15P11C105-455 에서 겪고 고친 그 버그다.
 */
class TokenStore(private val context: Context) {

    private val accessKey = stringPreferencesKey("accessToken")
    private val refreshKey = stringPreferencesKey("refreshToken")

    val isLoggedIn: Flow<Boolean> = context.authDataStore.data.map { it[accessKey] != null }

    suspend fun save(accessToken: String, refreshToken: String) {
        context.authDataStore.edit {
            it[accessKey] = accessToken
            it[refreshKey] = refreshToken
        }
    }

    /** 서버가 세션을 거부했을 때만 부른다 — 워치는 링크 화면으로 돌아간다 */
    suspend fun clear() {
        context.authDataStore.edit {
            it.remove(accessKey)
            it.remove(refreshKey)
        }
    }

    /** OkHttp 인터셉터(워커 스레드)에서 쓰는 동기 읽기 */
    fun accessTokenBlocking(): String? =
        runBlocking { context.authDataStore.data.first()[accessKey] }

    fun refreshTokenBlocking(): String? =
        runBlocking { context.authDataStore.data.first()[refreshKey] }

    fun hasTokensBlocking(): Boolean = accessTokenBlocking() != null
}
