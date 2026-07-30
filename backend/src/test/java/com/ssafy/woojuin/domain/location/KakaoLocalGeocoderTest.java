package com.ssafy.woojuin.domain.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
}
