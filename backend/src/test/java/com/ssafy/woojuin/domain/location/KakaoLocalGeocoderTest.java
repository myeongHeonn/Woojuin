package com.ssafy.woojuin.domain.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 카카오 로컬 응답 해석 단위 테스트. HTTP는 {@link KakaoLocalClient}를 목으로 두고
 * JSON 픽스처만 넣는다 (LlmQueryPlannerTest와 같은 방식).
 *
 * <p>가장 중요한 검증은 <b>x가 경도이고 문자열이라는 것</b>이다. 여길 뒤집으면 모든 핀이
 * 엉뚱한 곳에 꽂히는데 컴파일도 테스트도 통과해버린다.
 */
@ExtendWith(MockitoExtension.class)
class KakaoLocalGeocoderTest {

    private static final String ADDRESS_SEARCH = "/v2/local/search/address.json";
    private static final String KEYWORD_SEARCH = "/v2/local/search/keyword.json";
    private static final String COORD_TO_ADDRESS = "/v2/local/geo/coord2address.json";
    private static final String CATEGORY_SEARCH = "/v2/local/search/category.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KakaoLocalClient client;

    private KakaoLocalGeocoder geocoder;

    @BeforeEach
    void setUp() {
        geocoder = new KakaoLocalGeocoder(client);
    }

    private JsonNode documents(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    // ---------- 정방향 ----------

    @Test
    void 주소_검색_결과에서_좌표와_도로명주소를_읽는다() throws Exception {
        when(client.documents(eq(ADDRESS_SEARCH), anyMap())).thenReturn(documents("""
                [{
                  "address_name": "서울 성동구 성수동2가 333-12",
                  "x": "127.0561", "y": "37.5445",
                  "address": { "address_name": "서울 성동구 성수동2가 333-12" },
                  "road_address": { "address_name": "서울 성동구 아차산로17길 49" }
                }]"""));

        Optional<ResolvedLocation> result = geocoder.forwardAddress("서울 성동구 아차산로17길 49");

        assertThat(result).isPresent();
        // x=경도, y=위도. 문자열로 오는 값을 정확히 매핑해야 한다.
        assertThat(result.get().lat()).isEqualTo(37.5445);
        assertThat(result.get().lng()).isEqualTo(127.0561);
        assertThat(result.get().address()).isEqualTo("서울 성동구 아차산로17길 49");
    }

    @Test
    void road_address가_없으면_지번주소로_폴백한다() throws Exception {
        when(client.documents(eq(ADDRESS_SEARCH), anyMap())).thenReturn(documents("""
                [{
                  "address_name": "제주 제주시 한림읍 귀덕리 1234",
                  "x": "126.2398", "y": "33.3938",
                  "address": { "address_name": "제주 제주시 한림읍 귀덕리 1234" },
                  "road_address": null
                }]"""));

        assertThat(geocoder.forwardAddress("제주 제주시 한림읍 귀덕리 1234"))
                .get()
                .extracting(ResolvedLocation::address)
                .isEqualTo("제주 제주시 한림읍 귀덕리 1234");
    }

    @Test
    void 키워드_검색은_road_address_name_문자열을_쓴다() throws Exception {
        // 키워드 검색 응답은 road_address가 객체가 아니라 road_address_name 문자열이다.
        when(client.documents(eq(KEYWORD_SEARCH), anyMap())).thenReturn(documents("""
                [{
                  "place_name": "어니언 성수",
                  "address_name": "서울 성동구 성수동2가 277-135",
                  "road_address_name": "서울 성동구 아차산로9길 8",
                  "x": "127.0561", "y": "37.5445"
                }]"""));

        Optional<ResolvedLocation> result = geocoder.forwardKeyword("어니언 성수");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(37.5445);
        assertThat(result.get().address()).isEqualTo("서울 성동구 아차산로9길 8");
    }

    @Test
    void 주소_검색이_0건이면_키워드_검색으로_폴백한다() throws Exception {
        when(client.documents(eq(ADDRESS_SEARCH), anyMap())).thenReturn(documents("[]"));
        when(client.documents(eq(KEYWORD_SEARCH), anyMap())).thenReturn(documents("""
                [{ "place_name": "어니언 성수", "road_address_name": "서울 성동구 아차산로9길 8",
                   "x": "127.0561", "y": "37.5445" }]"""));

        assertThat(geocoder.forwardAddress("성수동 어니언")).isPresent();

        verify(client).documents(eq(ADDRESS_SEARCH), anyMap());
        verify(client).documents(eq(KEYWORD_SEARCH), anyMap());
    }

    @Test
    void 둘_다_0건이면_빈_결과() throws Exception {
        when(client.documents(any(), anyMap())).thenReturn(documents("[]"));

        assertThat(geocoder.forwardAddress("있을 수 없는 주소")).isEmpty();
    }

    @Test
    void documents가_null이어도_예외를_던지지_않는다() throws Exception {
        when(client.documents(any(), anyMap())).thenReturn(null);

        assertThat(geocoder.forwardAddress("서울 성동구 아차산로 49")).isEmpty();
    }

    @Test
    void 호출이_실패해도_예외를_던지지_않는다() throws Exception {
        // Geocoder 계약: 어떤 실패도 empty. 예외가 새면 @Transactional 프로세서가
        // rollback-only로 찍혀 이미 확보한 미리보기·본문까지 날아간다.
        when(client.documents(any(), anyMap())).thenThrow(new RuntimeException("timeout"));

        assertThat(geocoder.forwardAddress("서울 성동구 아차산로 49")).isEmpty();
        assertThat(geocoder.forwardKeyword("성수동 카페")).isEmpty();
        assertThat(geocoder.reverse(new GeoPoint(37.5445, 127.0561))).isEmpty();
    }

    @Test
    void 좌표가_숫자가_아니면_빈_결과() throws Exception {
        when(client.documents(eq(ADDRESS_SEARCH), anyMap())).thenReturn(documents("""
                [{ "x": "", "y": "abc", "address_name": "이상한 응답" }]"""));
        when(client.documents(eq(KEYWORD_SEARCH), anyMap())).thenReturn(documents("[]"));

        assertThat(geocoder.forwardAddress("서울 성동구 아차산로 49")).isEmpty();
    }

    @Test
    void 빈_질의는_호출하지_않는다() {
        assertThat(geocoder.forwardAddress(null)).isEmpty();
        assertThat(geocoder.forwardAddress("  ")).isEmpty();
        assertThat(geocoder.forwardKeyword(null)).isEmpty();

        verifyNoInteractions(client);
    }

    // ---------- 역방향 ----------

    @Test
    void 역지오코딩은_좌표를_x_y로_보내고_도로명주소를_읽는다() throws Exception {
        when(client.documents(eq(COORD_TO_ADDRESS), anyMap())).thenReturn(documents("""
                [{
                  "address": { "address_name": "서울 성동구 성수동2가 333-12" },
                  "road_address": { "address_name": "서울 성동구 아차산로17길 49" }
                }]"""));

        Optional<String> address = geocoder.reverse(new GeoPoint(37.5445, 127.0561));

        assertThat(address).contains("서울 성동구 아차산로17길 49");
        verify(client).documents(eq(COORD_TO_ADDRESS), eq(Map.of(
                "x", "127.0561",      // 경도가 x
                "y", "37.5445",       // 위도가 y
                "input_coord", "WGS84")));
    }

    @Test
    void 역지오코딩_0건이면_주소만_비운다() throws Exception {
        // 바다·국외 좌표는 정상적으로 0건이다. 좌표는 이미 확보했으니 주소만 없는 게 맞다.
        when(client.documents(eq(COORD_TO_ADDRESS), anyMap())).thenReturn(documents("[]"));

        assertThat(geocoder.reverse(new GeoPoint(37.5445, 127.0561))).isEmpty();
    }

    @Test
    void 역지오코딩에_null_좌표를_주면_호출하지_않는다() throws Exception {
        assertThat(geocoder.reverse(null)).isEmpty();

        verify(client, never()).documents(any(), anyMap());
    }

    // ---------- 주변 장소 (FR-053) ----------

    @Test
    void 주변_장소를_카테고리_그룹별로_모아_거리순으로_돌려준다() throws Exception {
        stubGroup("FD6", """
                [{
                  "place_name": "온화정", "category_name": "음식점 > 한식",
                  "x": "127.0562", "y": "37.5446", "distance": "120",
                  "road_address_name": "서울 성동구 성수이로 100"
                }]""");
        stubGroup("CE7", """
                [{
                  "place_name": "어니언 성수", "category_name": "음식점 > 카페",
                  "x": "127.0570", "y": "37.5450", "distance": "45",
                  "place_url": "http://place.map.kakao.com/26338954",
                  "road_address_name": "서울 성동구 아차산로9길 8"
                }]""");

        var result = geocoder.nearby(new GeoPoint(37.5445, 127.0561), false);
        var places = result.places();

        assertThat(result.expanded()).isFalse();
        assertThat(places).hasSize(2);
        // 그룹 순서(FD6 먼저)가 아니라 거리순이다 — 워치 화면의 첫 후보가 제일 가까운 곳이어야 한다
        assertThat(places.get(0).name()).isEqualTo("어니언 성수");
        assertThat(places.get(0).distanceMeters()).isEqualTo(45);
        assertThat(places.get(1).name()).isEqualTo("온화정");
        // x=경도, y=위도 — 뒤집히면 컴파일도 테스트도 통과한 채 핀이 엉뚱한 곳에 간다
        assertThat(places.get(0).point().lng()).isEqualTo(127.0570);
        assertThat(places.get(0).point().lat()).isEqualTo(37.5450);
        assertThat(places.get(0).placeUrl()).isEqualTo("http://place.map.kakao.com/26338954");
        assertThat(places.get(1).placeUrl()).isNull(); // 픽스처에 없으면 null — 필수 아님
    }

    @Test
    void 기본_검색은_후보가_한_곳뿐이어도_음식점_카페로_끝낸다() throws Exception {
        // 반경이 좁아 후보가 몇 개뿐인 건 흔하다 — 그때마다 알아서 넓히면 사용자가
        // "주변 더 찾기"로 고를 기회가 없어지므로, 2요청(쿼터 2)으로 끝내고 expanded=false
        stubGroup("FD6", """
                [{"place_name": "온화정", "x": "127.0562", "y": "37.5446", "distance": "20"}]""");

        var result = geocoder.nearby(new GeoPoint(37.5445, 127.0561), false);

        assertThat(result.expanded()).isFalse();
        assertThat(result.places()).hasSize(1);
        verify(client, times(2)).documents(eq(CATEGORY_SEARCH), anyMap());
    }

    @Test
    void 음식점_카페가_0건이면_요청하지_않아도_넓힌다() throws Exception {
        // 보여줄 것이 없으면 확장이 유일한 선택지다 — 워치를 한 번 더 왕복시키지 않는다.
        // 이미 0건인 FD6·CE7 을 다시 묻지 않으므로 총 18요청(기본 2 + 나머지 16)이다.
        when(client.documents(eq(CATEGORY_SEARCH), anyMap())).thenReturn(documents("[]"));

        var result = geocoder.nearby(new GeoPoint(37.5445, 127.0561), false);

        assertThat(result.expanded()).isTrue();
        verify(client, times(18)).documents(eq(CATEGORY_SEARCH), anyMap());
        verify(client, times(1)).documents(eq(CATEGORY_SEARCH),
                argThat(params -> "FD6".equals(params.get("category_group_code"))));
    }

    @Test
    void 확장_요청은_전_그룹을_뒤진다() throws Exception {
        when(client.documents(eq(CATEGORY_SEARCH), anyMap())).thenReturn(documents("[]"));

        var result = geocoder.nearby(new GeoPoint(37.5445, 127.0561), true);

        assertThat(result.expanded()).isTrue();
        verify(client, times(18)).documents(eq(CATEGORY_SEARCH), anyMap());
    }

    @Test
    void 넓힐_때는_동_이름_키워드로도_찾는다() throws Exception {
        // 카카오 category_group_code 는 "중요 카테고리만" 붙는 값이라 공장·회사·기숙사는
        // 비어 있고, 카테고리 검색은 그 코드가 필수라 그런 장소를 영원히 못 본다.
        // 동 이름은 주소로 매칭되고 키워드 검색은 코드가 선택이라, 이 경로가 유일하다.
        when(client.documents(eq(CATEGORY_SEARCH), anyMap())).thenReturn(documents("[]"));
        when(client.documents(eq(COORD_TO_ADDRESS), anyMap())).thenReturn(documents("""
                [{"address": {"region_3depth_name": "오선동"}}]"""));
        when(client.documents(eq(KEYWORD_SEARCH), anyMap())).thenReturn(documents("""
                [{"place_name": "삼성전자 광주사업장", "category_group_code": "",
                  "x": "126.8115", "y": "35.2053", "distance": "25"}]"""));

        var result = geocoder.nearby(new GeoPoint(35.2052, 126.8117), true);

        assertThat(result.places()).extracting(NearbyPlace::name)
                .containsExactly("삼성전자 광주사업장");
        verify(client).documents(eq(KEYWORD_SEARCH),
                argThat(params -> "오선동".equals(params.get("query"))
                        && "100".equals(params.get("radius"))));
    }

    @Test
    void 동_이름은_지번주소에서_읽는다() throws Exception {
        // road_address 쪽 region_3depth_name 은 빈 문자열인 곳이 흔하다(실측: 오선동).
        // 그걸 먼저 읽으면 질의가 비어 조용히 아무 일도 일어나지 않는다.
        when(client.documents(eq(CATEGORY_SEARCH), anyMap())).thenReturn(documents("[]"));
        when(client.documents(eq(COORD_TO_ADDRESS), anyMap())).thenReturn(documents("""
                [{"road_address": {"region_3depth_name": "", "road_name": "하남산단6번로"},
                  "address": {"region_3depth_name": "오선동"}}]"""));
        when(client.documents(eq(KEYWORD_SEARCH), anyMap())).thenReturn(documents("[]"));

        geocoder.nearby(new GeoPoint(35.2052, 126.8117), true);

        verify(client).documents(eq(KEYWORD_SEARCH),
                argThat(params -> "오선동".equals(params.get("query"))));
    }

    @Test
    void 키워드와_카테고리에_같은_장소가_걸리면_한_번만_싣는다() throws Exception {
        // 그룹끼리는 배타적이라 지금까지 중복이 없었지만, 키워드 검색은 그것들과 겹친다.
        String daycare = """
                [{"place_name": "삼성전자광주어린이집", "place_url": "http://place.map.kakao.com/25952506",
                  "x": "126.8102", "y": "35.2050", "distance": "144"}]""";
        stubGroup("PS3", daycare);
        when(client.documents(eq(COORD_TO_ADDRESS), anyMap())).thenReturn(documents("""
                [{"address": {"region_3depth_name": "오선동"}}]"""));
        when(client.documents(eq(KEYWORD_SEARCH), anyMap())).thenReturn(documents(daycare));

        var result = geocoder.nearby(new GeoPoint(35.2052, 126.8117), true);

        assertThat(result.places()).hasSize(1);
    }

    @Test
    void 기본_검색은_동_이름을_묻지_않는다() throws Exception {
        // 흔한 경로(음식점·카페가 잡히는 곳)는 성격도 속도도 그대로 둔다.
        stubGroup("FD6", """
                [{"place_name": "온화정", "x": "127.0562", "y": "37.5446", "distance": "20"}]""");

        geocoder.nearby(new GeoPoint(37.5445, 127.0561), false);

        verify(client, never()).documents(eq(COORD_TO_ADDRESS), anyMap());
        verify(client, never()).documents(eq(KEYWORD_SEARCH), anyMap());
    }

    @Test
    void 주변_검색_반경은_걸어서_1분_거리다() throws Exception {
        // "지금 서 있는 곳"을 저장하는 기능이다 — 반경이 커지면 엉뚱한 가게가 섞인다.
        // 값 자체가 제품 결정이라 조용히 늘어나지 않게 잠가 둔다.
        stubGroup("FD6", """
                [{"place_name": "온화정", "x": "127.0562", "y": "37.5446", "distance": "20"}]""");

        geocoder.nearby(new GeoPoint(37.5445, 127.0561), false);

        verify(client, times(2)).documents(eq(CATEGORY_SEARCH),
                argThat(params -> "100".equals(params.get("radius"))));
    }

    @Test
    void 주변_검색_실패는_빈_목록이다() throws Exception {
        when(client.documents(eq(CATEGORY_SEARCH), anyMap()))
                .thenThrow(new RuntimeException("timeout"));

        assertThat(geocoder.nearby(new GeoPoint(37.5445, 127.0561), false).places()).isEmpty();
    }

    /** 한 카테고리 그룹의 응답만 지정한다 — 나머지 그룹은 스텁이 없어 0건이 된다. */
    private void stubGroup(String groupCode, String json) throws Exception {
        when(client.documents(eq(CATEGORY_SEARCH),
                argThat(params -> groupCode.equals(params.get("category_group_code")))))
                .thenReturn(documents(json));
    }
}
