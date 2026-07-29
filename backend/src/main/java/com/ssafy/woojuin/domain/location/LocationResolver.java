package com.ssafy.woojuin.domain.location;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 위치 확보 전략 (FR-023). 싸고 정확한 것부터 시도하고 첫 성공에서 멈춘다.
 *
 * <p>전략만 여기서 정하고, "어떤 문자열이 후보인가"는 호출부(프로세서)가 정한다 — 프로세서마다
 * 가진 재료가 다르기 때문이다.
 *
 * <p>URL 아이템:
 * <ol>
 *   <li>URL에 박힌 좌표 파싱 — 좌표 자체는 네트워크 0회로 얻는다. 지도 공유 링크의 정답
 *       경로다. 주소는 없으므로 역지오코딩 1회로 채운다</li>
 *   <li>텍스트에서 뽑은 한국 주소를 지오코딩 — 좌표와 정규화된 주소를 함께 얻는다</li>
 *   <li>실패 → 위치 없음. <b>정상 결과다</b> (대부분의 아이템에 위치가 없다)</li>
 * </ol>
 *
 * <p>1단계가 성공하면 2단계를 아예 건너뛰므로 <b>아이템당 지오코딩은 최대 1번</b>이다
 * (카카오 주소→키워드 폴백 때문에 2단계의 HTTP 요청은 2회가 될 수 있다 —
 * {@link KakaoLocalGeocoder#forwardAddress} 참고). 그리고 그 1번은 아이템을 저장할 때
 * 한 번이고 지도를 열 때마다가 아니다.
 *
 * <p>묶음 F의 실제 AI 분석기가 오면 LLM이 뽑은 장소명이 1과 2 사이에 소스로 추가된다.
 * 그때 {@code AiAnalysis}를 확장하고 프로세서의 호출 위치를 AI 뒤로 옮기면 되며, 지금은
 * 아무도 채우지 않는 필드를 위해 공유 계약을 흔들지 않는다.
 */
@Slf4j
@Component
public class LocationResolver {

    private final MapLinkCoordinateParser mapLinkParser;
    private final KoreanAddressExtractor addressExtractor;
    private final Geocoder geocoder;

    public LocationResolver(MapLinkCoordinateParser mapLinkParser,
            KoreanAddressExtractor addressExtractor, Geocoder geocoder) {
        this.mapLinkParser = mapLinkParser;
        this.addressExtractor = addressExtractor;
        this.geocoder = geocoder;
    }

    /**
     * @param candidateUrls 원본 URL, 정규화된 URL, 리다이렉트 해소된 최종 URL (우선순위 순)
     * @param candidateTexts og:description → title → 본문 순. 앞쪽이 이 페이지의 주제를
     *                       설명하는 텍스트라 "다른 가게 주소"를 잡을 확률이 낮다
     */
    public Optional<ResolvedLocation> resolveForUrlItem(List<String> candidateUrls,
            List<String> candidateTexts) {

        Optional<GeoPoint> fromLink = mapLinkParser.parse(toArray(candidateUrls));
        if (fromLink.isPresent()) {
            // 좌표는 URL에서 공짜로 얻었지만 주소는 없다. 장소 패널과 지도 팝업이 주소를
            // 노출하고, 맛집·여행 링크가 이 경로의 주 시나리오라 그냥 두면 주소 없는 핀이
            // 다수가 된다 — 역지오코딩 1회를 들여 채운다. 저장 시 1회이고 지도를 열 때마다가
            // 아니라 비용이 작다. 실패하면 좌표만 남는다.
            return Optional.of(resolveForCoordinates(fromLink.get()));
        }

        Optional<String> address = addressExtractor.extract(toArray(candidateTexts));
        if (address.isEmpty()) {
            return Optional.empty();
        }
        // 지오코더가 검증기 역할을 한다 — 정규식이 잡은 게 실제 주소가 아니면 결과가 없고,
        // 그러면 그냥 버린다. 이 덕분에 정규식 오탐이 무해해진다.
        Optional<ResolvedLocation> geocoded = geocoder.forwardAddress(address.get());
        if (geocoded.isEmpty()) {
            log.debug("주소 후보를 지오코딩하지 못해 버린다: '{}'", address.get());
        }
        return geocoded;
    }

    /**
     * EXIF 등으로 좌표를 이미 확보한 경우. 역지오코딩으로 주소만 채운다.
     *
     * <p>역지오코딩이 실패해도 <b>좌표는 그대로 반환한다</b> — 핀이 목적이고 주소는 장식이다.
     */
    public ResolvedLocation resolveForCoordinates(GeoPoint point) {
        return new ResolvedLocation(point, geocoder.reverse(point).orElse(null));
    }

    private String[] toArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }
}
