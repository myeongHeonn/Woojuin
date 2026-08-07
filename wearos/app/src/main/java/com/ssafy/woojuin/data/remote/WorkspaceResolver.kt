package com.ssafy.woojuin.data.remote

/**
 * 저장·검색이 쓸 개인 스페이스 id 를 찾는다.
 *
 * 세 리포지토리(장소·음성·검색)가 같은 값을 필요로 하는데, 폴백 규칙이 자명하지 않아서
 * 공용으로 둔다 — **프로필의 `personalSpaceId` 가 null 인 계정이 실존한다**(구경로 가입).
 * 실기기에서 저장이 통째로 실패해 발견한 자리이므로, 규칙이 여러 벌로 복사되면 한 곳만
 * 고치고 나머지를 놓치게 된다.
 *
 * 한 번 찾으면 프로세스 동안 재사용한다 — 계정이 바뀌는 건 재링크뿐이고 그때는 앱이
 * 링크 화면으로 되돌아가므로 무효화 경로가 따로 필요하지 않다.
 */
class WorkspaceResolver(private val api: WoojuinApi) {

    @Volatile
    private var cached: Long? = null

    fun personalSpaceId(): Long {
        cached?.let { return it }
        val profile = api.authorizedGet("/users/me").getJSONObject("data")
        val id = if (!profile.isNull("personalSpaceId")) {
            profile.getLong("personalSpaceId")
        } else {
            // 웹 사이드바가 쓰는 것과 같은 목록에서 PERSONAL 을 찾는다
            val workspaces = api.authorizedGet("/workspaces").getJSONArray("data")
            (0 until workspaces.length())
                .map { workspaces.getJSONObject(it) }
                .firstOrNull { it.getString("type") == "PERSONAL" }
                ?.getLong("id")
                ?: throw IllegalStateException("저장할 워크스페이스가 없습니다")
        }
        cached = id
        return id
    }
}
