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
 */
public interface Geocoder {

    /** 도로명·지번 주소 문자열 → 좌표. 본문에서 정규식으로 뽑은 주소가 여기로 온다. */
    Optional<ResolvedLocation> forwardAddress(String address);

    /** 상호·장소명 → 좌표. "성수동 어니언" 처럼 주소가 아니라 이름만 있을 때. */
    Optional<ResolvedLocation> forwardKeyword(String keyword);

    /** 좌표 → 주소 문자열. EXIF GPS로 좌표만 얻은 IMAGE 아이템의 address를 채운다. */
    Optional<String> reverse(GeoPoint point);
}
