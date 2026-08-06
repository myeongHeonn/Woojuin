package com.ssafy.woojuin.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.concurrent.futures.await
import androidx.wear.remote.interactions.RemoteActivityHelper

private const val TAG = "WoojuinPhone"

/**
 * 저장한 링크를 **페어링된 휴대폰에서 연다**.
 *
 * <p>이 화면의 원래 구현은 "휴대폰으로 보냈어요"라고만 말하고 아무것도 보내지 않았다.
 * 시연에서 누르면 거짓 확인이 뜨는 상태였다 — 근처 알림 칩을 걷은 것과 같은 종류의 문제라,
 * 걷는 대신 실제로 열도록 했다(손목에서 찾고 폰에서 이어보는 것이 이 앱의 동선이다).
 *
 * <p>{@link RemoteActivityHelper} 는 워치의 요청을 폰으로 넘겨 브라우저로 연다 —
 * <b>폰에 우리 앱이 없어도 된다.</b> 대신 페어링이 없으면 실패하므로 성공 여부를 돌려주고,
 * 화면이 그에 맞는 말을 하게 한다.
 */
object PhoneLauncher {

    /**
     * @return 폰에 요청이 전달됐으면 true. 페어링이 없거나 폰이 거부하면 false
     */
    suspend fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(url))
        return try {
            // nodeId 를 주지 않으면 연결된 기기 전부에 시도한다 — 폰이 하나뿐인 보통의 경우에
            // 그게 맞고, 어느 노드가 폰인지 우리가 골라낼 필요도 없다
            RemoteActivityHelper(context).startRemoteActivity(intent).await()
            Log.d(TAG, "휴대폰에서 열기 성공")
            true
        } catch (e: Exception) {
            // RemoteActivityHelper.RemoteIntentException(페어링 없음)·취소 모두 여기로 온다
            Log.d(TAG, "휴대폰에서 열기 실패: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
