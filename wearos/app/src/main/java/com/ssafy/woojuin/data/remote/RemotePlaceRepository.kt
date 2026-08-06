package com.ssafy.woojuin.data.remote

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ssafy.woojuin.domain.model.PlaceCandidate
import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SavedItemType
import com.ssafy.woojuin.domain.repository.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 위치 저장(FR-053)의 실서버 구현 — FakePlaceRepository 의 후임.
 *
 * 흐름: FusedLocation 현재 좌표 → GET /places/nearby (카카오맵 링크가 있는 실제 장소만) →
 * 고른 후보의 카카오맵 링크를 **기존 URL 아이템으로** 저장한다. URL 파이프라인이 크롤로
 * 좌표(스태틱맵)·제목·요약·썸네일을 만들므로 장소 전용 저장 경로가 따로 없다 — 웹에서
 * 링크를 저장한 것과 완전히 같은 아이템이 된다.
 */
class RemotePlaceRepository(
    context: Context,
    private val api: WoojuinApi,
    private val workspaces: WorkspaceResolver,
) : PlaceRepository {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    // 초기값 true — 목록이 없는 동안 "주변 더 찾기"부터 보이면 이상하다
    private val _lastExpanded = MutableStateFlow(true)
    override val lastExpanded: StateFlow<Boolean> = _lastExpanded.asStateFlow()

    private val _lastSavedPlace = MutableStateFlow<PlaceCandidate?>(null)
    override val lastSavedPlace: StateFlow<PlaceCandidate?> = _lastSavedPlace.asStateFlow()

    override suspend fun nearbyCandidates(expand: Boolean): List<PlaceCandidate> =
        withContext(Dispatchers.IO) {
        val location = currentLocation()
        val query = "lat=${location.first}&lng=${location.second}" +
            if (expand) "&expand=true" else ""
        val data = api.authorizedGet("/places/nearby?$query")
            .getJSONObject("data")
        val array = data.getJSONArray("candidates")
        val candidates = buildList {
            for (i in 0 until array.length()) {
                val c = array.getJSONObject(i)
                // optString 은 JSON null 을 문자열 "null" 로 준다 — isNull 을 먼저 봐야 한다
                val category = if (c.isNull("category")) ""
                else c.getString("category").substringAfterLast(" > ")
                add(
                    PlaceCandidate(
                        // 서버 후보에는 id 가 없다(저장 전이므로) — 화면 키 용도로만 쓴다
                        id = "nearby-$i",
                        name = c.getString("name"),
                        category = category,
                        distanceMeters = c.optInt("distanceMeters", 0),
                        lat = c.getDouble("lat"),
                        lng = c.getDouble("lng"),
                        address = if (c.isNull("address")) null else c.getString("address"),
                        placeUrl = if (c.isNull("placeUrl")) null else c.getString("placeUrl"),
                    ),
                )
            }
        }
        // 필드가 없는(구버전) 서버면 true — 헛된 확장 요청을 반복하지 않는 쪽이 안전하다
        _lastExpanded.value = data.optBoolean("expanded", true)
        candidates
    }

    override suspend fun savePlace(candidate: PlaceCandidate): SavedItem =
        withContext(Dispatchers.IO) {
            // 카카오맵 장소 링크를 웹과 같은 URL 아이템으로 저장한다 — 크롤·AI 는 서버가
            // 백그라운드로 진행하므로 워치는 201 응답이면 끝이다(폴링 없음)
            val placeUrl = candidate.placeUrl
                ?: throw IllegalStateException("장소 링크가 없는 후보입니다")
            val body = JSONObject()
                .put("type", "URL")
                .put("url", placeUrl)
            val data = api.authorized("/workspaces/${workspaces.personalSpaceId()}/items", body)
                .getJSONObject("data")

            _lastSavedPlace.value = candidate
            SavedItem(
                id = data.getLong("itemId").toString(),
                type = SavedItemType.PLACE,
                title = candidate.name,
                summary = candidate.address.orEmpty(),
                savedAtLabel = "방금 전",
                sourceLabel = "장소 저장",
                distanceLabel = candidate.distanceLabel,
            )
        }


    /**
     * 현재 좌표 (위도, 경도). 권한은 화면이 먼저 받는다 — 없으면 SecurityException 이
     * 그대로 흘러 화면의 "위치를 찾지 못했어요" 로 떨어진다.
     */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Pair<Double, Double> =
        suspendCancellableCoroutine { continuation ->
            fusedClient.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(10_000)
                    .build(),
                null,
            ).addOnSuccessListener { location ->
                if (location == null) {
                    continuation.resumeWithException(IllegalStateException("위치를 확인할 수 없습니다"))
                } else {
                    continuation.resume(location.latitude to location.longitude)
                }
            }.addOnFailureListener { continuation.resumeWithException(it) }
        }
}
