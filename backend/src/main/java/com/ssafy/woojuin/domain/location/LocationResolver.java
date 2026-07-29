package com.ssafy.woojuin.domain.location;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 위치 확보 전략 (FR-023).
 *
 * <p><b>원칙: 좌표는 지도에서만 온다. 추측하지 않는다.</b> 받아들이는 소스는 셋뿐이다 —
 * 지도 공유 링크, 페이지에 임베드된 지도, 사진의 EXIF GPS. 전부 사람이 명시적으로 지도에
 * 찍은 위치라서 "이 콘텐츠의 장소가 어디인가"를 추론할 필요가 없다.
 *
 * <p><b>본문 텍스트에서 주소를 정규식으로 뽑는 경로는 의도적으로 없다.</b> 초기엔 있었지만
 * 걷어냈다. 이유는 정확도보다 <b>제품 정의</b>다 — 지식·기술 글을 저장했을 때 지도에 핀이
 * 뜨는 건 사용자가 원하는 동작이 아니고, 반대로 지도에서 공유한 링크와 사진은 뜨는 게 유용하다.
 * 글에 흘러가듯 적힌 주소(회사 footer의 사업자 주소, "근처 다른 가게", 행사장 언급)를 그 글의
 * 주제로 단정할 방법이 정규식에는 없다.
 *
 * <p>이 결정으로 잃는 게 거의 없다는 것도 확인했다 — 맛집 블로그는 본문에 지도를 임베드하기
 * 때문에 {@link MapLinkCoordinateParser}가 그 핀을 그대로 읽는다. 같은 글에서 두 방식을
 * 비교했을 때 임베드 핀이 본문 주소 지오코딩보다 26m 더 정확했다(글쓴이가 직접 찍은 위치다).
 *
 * <p>URL 아이템:
 * <ol>
 *   <li>후보 URL들(원본·정규화·최종 URL → 페이지가 실어준 지도 이미지 → 본문에 임베드된 지도)에서
 *       좌표를 파싱한다. <b>네트워크 0회</b>다</li>
 *   <li>좌표를 얻었으면 역지오코딩 1회로 주소를 채운다 — 장소 패널과 팝업이 주소를 노출한다</li>
 *   <li>좌표가 없으면 위치 없음. <b>정상 결과다</b> (대부분의 아이템에 위치가 없다)</li>
 * </ol>
 *
 * <p>그래서 <b>아이템당 지오코딩은 최대 1회</b>이고, 그것도 좌표를 이미 얻은 경우에만 나가는
 * 역방향 호출이다. 저장할 때 한 번이고 지도를 열 때마다가 아니다.
 *
 * <p>묶음 F의 AI 분석기가 오면 LLM이 글의 <i>주제</i> 장소를 뽑아줄 수 있다. 그건 정규식이
 * 못 하던 "이 글이 어느 장소에 관한 글인가"를 실제로 판단하는 것이라 다시 검토할 가치가 있다.
 * 그때 이 클래스에 소스를 하나 앞에 끼우고 {@link Geocoder#forwardKeyword}를 쓰면 된다.
 */
@Slf4j
@Component
public class LocationResolver {

    private final MapLinkCoordinateParser mapLinkParser;
    private final Geocoder geocoder;

    public LocationResolver(MapLinkCoordinateParser mapLinkParser, Geocoder geocoder) {
        this.mapLinkParser = mapLinkParser;
        this.geocoder = geocoder;
    }

    /**
     * @param candidateUrls 원본 URL, 정규화된 URL, 리다이렉트 해소된 최종 URL, 페이지 메타의 지도
     *                      이미지, 본문에 임베드된 지도 — 이 순서로 넘기면 정확한 것이 먼저 걸린다
     */
    public Optional<ResolvedLocation> resolveForUrlItem(List<String> candidateUrls) {
        return mapLinkParser.parse(toArray(candidateUrls))
                .map(this::resolveForCoordinates);
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
