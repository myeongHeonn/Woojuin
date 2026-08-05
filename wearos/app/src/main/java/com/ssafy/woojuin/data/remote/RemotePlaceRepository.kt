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
 * 흐름: FusedLocation 현재 좌표 → GET /places/nearby (첫 후보는 항상 "현재 위치") →
 * 고른 후보를 POST /workspaces/{개인}/places 로 저장. 서버가 좌표·주소·카카오맵 링크를
 * 아이템에 실어 저장 즉시 DONE 이라, 워치는 응답 한 번으로 끝난다.
 */
class RemotePlaceRepository(
    context: Context,
    private val api: WoojuinApi,
) : PlaceRepository {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _lastCandidates = MutableStateFlow<List<PlaceCandidate>>(emptyList())
    override val lastCandidates: StateFlow<List<PlaceCandidate>> = _lastCandidates.asStateFlow()

    private val _lastSavedPlace = MutableStateFlow<PlaceCandidate?>(null)
    override val lastSavedPlace: StateFlow<PlaceCandidate?> = _lastSavedPlace.asStateFlow()

    /** 저장할 워크스페이스 — 개인 스페이스 id. 프로필에서 한 번 받아 프로세스 동안 재사용한다 */
    @Volatile
    private var personalSpaceId: Long? = null

    override suspend fun nearbyCandidates(): List<PlaceCandidate> = withContext(Dispatchers.IO) {
        val location = currentLocation()
        val data = api.authorizedGet("/places/nearby?lat=${location.first}&lng=${location.second}")
            .getJSONObject("data")
        val array = data.getJSONArray("candidates")
        val candidates = buildList {
            for (i in 0 until array.length()) {
                val c = array.getJSONObject(i)
                // optString 은 JSON null 을 문자열 "null" 로 준다 — isNull 을 먼저 봐야 한다
                val category = if (c.isNull("category")) null
                else c.getString("category").substringAfterLast(" > ")
                val address = if (c.isNull("address")) null else c.getString("address")
                add(
                    PlaceCandidate(
                        // 서버 후보에는 id 가 없다(저장 전이므로) — 화면 키 용도로만 쓴다
                        id = "nearby-$i",
                        name = c.getString("name"),
                        // "현재 위치" 후보는 카테고리가 없다 — 부제 자리에 주소를 보여준다
                        category = category ?: address ?: "내 위치",
                        distanceMeters = c.optInt("distanceMeters", 0),
                        lat = c.getDouble("lat"),
                        lng = c.getDouble("lng"),
                        address = address,
                        placeUrl = if (c.isNull("placeUrl")) null else c.getString("placeUrl"),
                    ),
                )
            }
        }
        _lastCandidates.value = candidates
        candidates
    }

    override suspend fun savePlace(candidate: PlaceCandidate): SavedItem =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("name", candidate.name)
                .put("lat", candidate.lat)
                .put("lng", candidate.lng)
                .put("address", candidate.address)
                .put("placeUrl", candidate.placeUrl)
            val data = api.authorized("/workspaces/${workspaceId()}/places", body)
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

    /** 저장 직후 취소 — 아이템을 휴지통으로 보낸다(웹과 같은 삭제 규칙, 영구 삭제 아님) */
    override suspend fun undo(itemId: String) {
        withContext(Dispatchers.IO) { api.authorizedDelete("/items/$itemId") }
        _lastSavedPlace.value = null
    }

    private fun workspaceId(): Long {
        personalSpaceId?.let { return it }
        // 프로필의 personalSpaceId 가 null 인 계정이 실존한다(구경로 가입) — 목록의
        // PERSONAL 워크스페이스로 폴백한다. 웹 사이드바가 쓰는 것과 같은 목록이다
        val profile = api.authorizedGet("/users/me").getJSONObject("data")
        val id = if (!profile.isNull("personalSpaceId")) {
            profile.getLong("personalSpaceId")
        } else {
            val workspaces = api.authorizedGet("/workspaces").getJSONArray("data")
            (0 until workspaces.length())
                .map { workspaces.getJSONObject(it) }
                .firstOrNull { it.getString("type") == "PERSONAL" }
                ?.getLong("id")
                ?: throw IllegalStateException("저장할 워크스페이스가 없습니다")
        }
        personalSpaceId = id
        return id
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
