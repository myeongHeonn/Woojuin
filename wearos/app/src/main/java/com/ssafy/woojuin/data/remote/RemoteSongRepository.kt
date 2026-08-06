package com.ssafy.woojuin.data.remote

import android.util.Log
import com.ssafy.woojuin.data.speech.MicRecorder
import com.ssafy.woojuin.data.speech.WavEncoder
import com.ssafy.woojuin.domain.model.RecognizedSong
import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SavedItemType
import com.ssafy.woojuin.domain.repository.SongRecognitionEvent
import com.ssafy.woojuin.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "WoojuinSong"

/**
 * 들리는 노래를 인식해 저장한다 (FR-055) — [com.ssafy.woojuin.data.fake.FakeSongRepository] 의 후임.
 *
 * <p><b>저장은 URL 아이템으로 한다.</b> 서버가 준 곡 페이지 링크를 기존 URL 저장 경로
 * ({@code POST /workspaces/{id}/items})로 보내면, 크롤·AI 파이프라인이 제목·요약·썸네일을
 * 채운다 — 장소 저장이 카카오맵 링크를 저장하는 것과 같은 방식이라 서버 스키마 변경이 없다.
 * 그래서 이 클래스에는 "노래 전용 저장 API" 가 없다.
 *
 * <p>녹음은 [MicRecorder.recordFixed] 로 12초 고정이다. 말할 때 쓰는 무음 감지를 그대로
 * 쓰면 간주·페이드에서 끊겨 지문에 쓸 길이가 모자란다.
 */
class RemoteSongRepository(
    private val api: WoojuinApi,
    private val workspaces: WorkspaceResolver,
) : SongRepository {

    private val _lastMatched = MutableStateFlow<RecognizedSong?>(null)
    override val lastMatched: StateFlow<RecognizedSong?> = _lastMatched.asStateFlow()

    override fun recognize(): Flow<SongRecognitionEvent> = channelFlow {
        val recorder = MicRecorder(
            onStarted = { Log.d(TAG, "녹음 시작") },
            // 음량은 쓰지 않는다 — 노래 화면은 경과 시간으로 진행을 보여준다
            onLevel = {},
        )

        // 경과 초를 화면에 흘려보낸다(화면이 이미 쓰고 있는 이벤트다) — 녹음과 나란히 돈다
        val ticker = launch {
            for (second in 0..RECORD_SECONDS) {
                trySend(SongRecognitionEvent.Listening(second))
                delay(1_000)
            }
        }
        val recorded = try {
            withContext(Dispatchers.IO) {
                val scope = this
                recorder.recordFixed(RECORD_SECONDS) { scope.isActive }
            }
        } finally {
            ticker.cancel()
        }
        if (recorded == null || recorded.pcm.isEmpty()) {
            Log.d(TAG, "마이크를 열 수 없다")
            trySend(SongRecognitionEvent.NoMatch)
            return@channelFlow
        }

        val wav = WavEncoder.wrap(recorded.pcm, MicRecorder.SAMPLE_RATE)
        Log.d(TAG, "인식 요청 — ${wav.size / 1024}KB")
        val song = withContext(Dispatchers.IO) { recognizeOnServer(wav) }
        if (song == null) {
            trySend(SongRecognitionEvent.NoMatch)
        } else {
            _lastMatched.value = song
            trySend(SongRecognitionEvent.Matched(song))
        }
    }

    /**
     * @return 찾은 곡, 못 찾았으면 null. 서버 실패는 예외로 올라가 화면이 오류를 보여준다
     *   ([SongRecognitionEvent.NoMatch] 와 구분된다 — "못 찾음"과 "실패"는 다른 말이다)
     */
    private fun recognizeOnServer(wav: ByteArray): RecognizedSong? {
        val data = api.authorizedUpload(
            path = "/music/recognitions",
            fieldName = "file",
            filename = "clip.wav",
            contentType = "audio/wav",
            bytes = wav,
        ).getJSONObject("data")
        if (!data.optBoolean("found", false)) {
            Log.d(TAG, "곡을 찾지 못했다")
            return null
        }
        val link = text(data, "link")
        if (link == null) {
            // 링크가 없으면 저장할 수 없다 — 서버가 걸러 주지만 여기서도 지킨다
            Log.d(TAG, "링크 없는 결과 — 못 찾은 것으로 본다")
            return null
        }
        return RecognizedSong(
            title = text(data, "title") ?: "제목 없음",
            artist = text(data, "artist") ?: "",
            albumLabel = "",
            link = link,
        )
    }

    /**
     * 인식한 곡을 저장한다 — **곡 링크를 URL 아이템으로** 보낸다(위 javadoc 참고).
     * 201 이면 끝이고 폴링하지 않는다. 제목·썸네일은 서버 파이프라인이 채운다.
     */
    override suspend fun saveSong(song: RecognizedSong): SavedItem = withContext(Dispatchers.IO) {
        val link = song.link ?: throw IllegalStateException("저장할 곡 링크가 없습니다")
        val workspaceId = workspaces.personalSpaceId()
        val body = JSONObject().put("type", "URL").put("url", link)
        val data = api.authorized("/workspaces/$workspaceId/items", body).getJSONObject("data")
        SavedItem(
            id = data.optLong("itemId").toString(),
            // 서버가 URL 로 저장했으므로 화면도 링크로 보여준다(아이콘 규칙은 -492 결정 3)
            type = SavedItemType.LINK,
            title = song.title,
            summary = if (song.artist.isBlank()) "" else song.artist,
            savedAtLabel = "방금",
            sourceLabel = null,
        )
    }

    private fun text(json: JSONObject, key: String): String? {
        if (json.isNull(key)) return null
        val value = json.optString(key, "").trim()
        return value.ifEmpty { null }
    }

    private companion object {
        /**
         * 녹음 길이. 12초는 실측 근거가 있다 — 전영호 - Butter-Fly 를 12초 16kHz 모노 WAV
         * (375KB)로 보내 601ms 에 인식됐다. 더 짧으면 지문이 모자라고, 더 길면 사용자가
         * 팔을 들고 기다리는 시간만 늘어난다.
         */
        const val RECORD_SECONDS = 12
    }
}
