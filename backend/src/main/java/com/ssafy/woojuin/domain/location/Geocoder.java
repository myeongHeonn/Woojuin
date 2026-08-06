package com.ssafy.woojuin.domain.location;

import java.util.Optional;

/**
 * 주소·장소명 ↔ 좌표 변환 (FR-023). 카카오 로컬 API가 기본 구현이고, REST 키가 없으면
 * {@link NoOpGeocoder}가 올라간다 — AGENTS.md의 "지도 로직은 어댑터/인터페이스로 추상화,
 * 좌표는 lat/lng 원시값" 방침을 따른다.
 *
 * <p><b>구현체는 절대 예외를 던지지 않는다.</b> 실패·0건·타임아웃 모두 {@code Optional.empty()}다.
 * {@link com.ssafy.woojuin.domain.ai.AiAnalyzer}와 같은 계약이고 이유도 같은데, 여기서는
 * 이유가 하나 더 있다 — 호출부가 {@code @Transactional} 프로세서 안이라서, 예외가 새어나가면
 * 트랜잭션이 rollback-only로 찍혀 이미 확보한 미리보기·본문·썸네일까지 전부 날아가고
 * 파이프라인이 (AI 호출까지 포함해) 처음부터 재실행된다.
 *
 * <p>정방향을 주소용·키워드용으로 나눈 건 호출부가 자기가 든 문자열의 종류를 실제로 알고
 * 있고, 카카오의 두 엔드포인트 동작이 많이 다르기 때문이다.
 *
 * <p><b>지금 파이프라인이 쓰는 건 {@link #reverse}뿐이다.</b> 좌표는 지도에서만 받기로 했고
 * (이유는 {@link LocationResolver} javadoc), 지도에서 온 좌표에 주소를 붙이는 게 역방향이다.
 * 정방향 둘은 <b>묶음 F의 AI 분석기</b>를 위해 남겨 뒀다 — LLM이 글의 주제 장소를 이름이나
 * 주소로 뽑아주면 그때 좌표로 바꿔야 하고, 그건 정규식이 못 하던 "이 글이 어느 장소에 관한
 * 글인가"를 실제로 판단한 결과라 다시 쓸 값이 있다. 라이브 API로 계약을 검증해 둔 코드라
 * 지웠다가 다시 만드는 쪽이 손해다({@code KakaoLocalGeocoderTest}가 계속 지킨다).
 */
public interface Geocoder {

    /** 도로명·지번 주소 문자열 → 좌표. 현재 미사용 — 위 javadoc 참고. */
    Optional<ResolvedLocation> forwardAddress(String address);

    /** 상호·장소명 → 좌표. "성수동 어니언" 처럼 이름만 있을 때. 현재 미사용 — 위 javadoc 참고. */
    Optional<ResolvedLocation> forwardKeyword(String keyword);

    /** 좌표 → 주소 문자열. EXIF GPS로 좌표만 얻은 IMAGE 아이템의 address를 채운다. */
    Optional<String> reverse(GeoPoint point);

    /**
     * 좌표 주변의 장소 후보 (거리순). 워치 위치 저장(FR-053)이 "지금 있는 곳 고르기"에 쓴다.
     * 다른 메서드와 같은 계약 — 실패·0건 모두 빈 목록이고 예외를 던지지 않는다.
     * NoOp(키 없음)에서는 빈 목록이라, 워치 화면엔 "현재 위치" 후보만 남는다.
     *
     * <p>기본은 음식점·카페만 뒤진다(쿼터 2). {@code expand}가 참이면 전 카테고리로
     * 넓힌다 — 워치의 "주변 더 찾기"가 그것이다. 기본 검색이 <b>0건</b>이면 요청하지
     * 않아도 넓힌다(그때는 확장이 유일한 선택지다). 몇 개만 나온 경우엔 넓히지 않는다.
     */
    NearbySearch nearby(GeoPoint point, boolean expand);

    /**
     * {@link #nearby} 결과. {@code expanded}는 전 카테고리를 이미 뒤졌다는 뜻으로,
     * 클라이언트가 이 응답에 대고 확장 요청을 또 보내봐야 같은 결과라는 신호다.
     */
    record NearbySearch(java.util.List<NearbyPlace> places, boolean expanded) {}
}
