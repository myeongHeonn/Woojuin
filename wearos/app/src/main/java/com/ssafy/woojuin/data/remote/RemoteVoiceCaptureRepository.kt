package com.ssafy.woojuin.data.remote

import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SavedItemType
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.domain.repository.SpeechSource
import com.ssafy.woojuin.domain.repository.VoiceCaptureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 음성 저장(FR-054 1단계)의 실서버 구현 — FakeVoiceCaptureRepository 의 후임.
 *
 * 흐름: STT 로 받은 문장을 **MEMO 아이템으로 저장**한다
 * (`POST /workspaces/{id}/items`, type MEMO). 웹에서 메모를 적어 저장한 것과 같은 아이템이라
 * 제목·요약·분류·임베딩을 서버가 백그라운드로 만든다 — 즉 저장 직후 검색에 바로 걸리지는
 * 않는다(AI 파이프라인이 끝나야 요약·임베딩이 채워진다).
 *
 * 인식은 [SpeechSource] 에 그대로 위임한다. STT 자체는 이 클래스의 관심사가 아니다 —
 * 구글 서버 인식이든 온디바이스든 [listen] 계약만 지키면 된다.
 */
class RemoteVoiceCaptureRepository(
    private val api: WoojuinApi,
    private val workspaces: WorkspaceResolver,
    private val speech: SpeechSource,
) : VoiceCaptureRepository {

    private val _lastSaved = MutableStateFlow<SavedItem?>(null)
    override val lastSaved: StateFlow<SavedItem?> = _lastSaved.asStateFlow()

    override fun listen(): Flow<SpeechEvent> = speech.listen()

    /**
     * 이름은 fake 시절의 "로컬 우선 저장"에서 왔지만 실구현은 **서버에 바로 보낸다.**
     * 오프라인 게이트가 통신 없는 상태에서 앱 자체를 막으므로 로컬 큐가 필요하지 않다.
     * 201 이면 끝 — 장소 저장과 같은 계약이라 폴링하지 않는다.
     */
    override suspend fun saveLocal(text: String): SavedItem = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("type", "MEMO")
            .put("content", text)
        val data = api.authorized("/workspaces/${workspaces.personalSpaceId()}/items", body)
            .getJSONObject("data")

        val item = SavedItem(
            id = data.getLong("itemId").toString(),
            type = SavedItemType.VOICE,
            // 서버 제목은 AI 가 나중에 붙인다 — 지금 보여줄 수 있는 건 말한 문장 자체다
            title = text,
            summary = "AI가 정리하고 있어요",
            savedAtLabel = "방금",
            sourceLabel = "음성 메모",
        )
        _lastSaved.value = item
        item
    }

    /**
     * 되돌리기는 휴지통 이동(soft delete)이다. 저장 완료 화면의 3초 실행 취소가 부른다.
     * 경로에 워크스페이스가 없다 — 아이템이 자기 workspaceId 를 들고 있어서다.
     *
     * 실패해도 삼킨다. 화면은 이미 홈으로 넘어갔고 여기서 오류를 띄울 자리가 없다 —
     * 남은 아이템은 웹 휴지통에서 지울 수 있으므로 되돌릴 수 없는 손실이 아니다.
     */
    override suspend fun undo(itemId: String) {
        withContext(Dispatchers.IO) {
            runCatching { api.authorizedDelete("/items/$itemId") }
        }
        if (_lastSaved.value?.id == itemId) _lastSaved.value = null
    }
}
