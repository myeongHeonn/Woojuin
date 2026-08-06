package com.ssafy.woojuin.domain.location;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 로컬 API 기반 {@link Geocoder}.
 *
 * <p><b>가장 흔한 버그: {@code x}가 경도, {@code y}가 위도이고 둘 다 JSON 문자열이다.</b>
 * 숫자로 오지 않으므로 파싱이 필요하고, 순서를 절대 가정하면 안 된다.
 *
 * <p>주소 표기는 {@code road_address.address_name}(도로명) → {@code address.address_name}(지번)
 * → 최상위 {@code address_name} 순으로 폴백한다. 도로명만 있는 곳과 지번만 있는 곳이 각각
 * 있어서 어느 한쪽이 null일 수 있다.
 *
 * <p><b>재시도하지 않는다.</b> Redis 스트림이 이미 {@code max-delivery: 3}으로 메시지를
 * 재배달하므로, 클라이언트에서 또 재시도하면 쿼터 소모가 배로 늘어난다.
 */
@Slf4j
public class KakaoLocalGeocoder implements Geocoder {

    private static final String ADDRESS_SEARCH = "/v2/local/search/address.json";
    private static final String KEYWORD_SEARCH = "/v2/local/search/keyword.json";
    private static final String COORD_TO_ADDRESS = "/v2/local/geo/coord2address.json";
    private static final String CATEGORY_SEARCH = "/v2/local/search/category.json";

    /**
     * 주변 검색은 2단이다. 카테고리 검색 API는 요청당 코드 하나가 필수라 "전부"를 한 번에
     * 물을 수 없고 그룹 수만큼 요청이 나가므로, 기본은 "지금 있는 곳 저장"의 대다수인
     * 음식점·카페 2그룹만 묻는다(쿼터 2). 호출자가 확장을 요청하면 나머지 16그룹까지
     * 넓힌다(쿼터 18) — 워치의 "주변 더 찾기"가 그것이다. 호출은 {@link #nearbyExecutor}로
     * 병렬이라 지연은 그룹 수에 비례하지 않는다.
     *
     * <p><b>0건일 때만 알아서 넓힌다.</b> 반경이 좁아 후보 몇 개만 나오는 건 흔한 일이라
     * 그때 넓히면 사용자가 고를 기회가 없어지지만, 아예 0건이면 보여줄 것이 없어 확장이
     * 유일한 선택지다 — 워치를 한 번 더 왕복시킬 이유가 없다.
     */
    private static final java.util.List<String> NEARBY_PRIMARY_GROUPS = java.util.List.of("FD6", "CE7");
    private static final java.util.List<String> NEARBY_SECONDARY_GROUPS = java.util.List.of(
            "MT1", "CS2", "PS3", "SC4", "AC5", "PK6", "OL7", "SW8", "BK9",
            "CT1", "AG2", "PO3", "AT4", "AD5", "HP8", "PM9");
    private static final java.util.List<String> NEARBY_ALL_GROUPS = java.util.stream.Stream
            .concat(NEARBY_PRIMARY_GROUPS.stream(), NEARBY_SECONDARY_GROUPS.stream()).toList();
    /**
     * "지금 있는 곳"의 반경. 걸어서 1분 거리다 — 이 기능은 <b>지금 서 있는 장소</b>를
     * 저장하는 것이라, 200m 밖 가게가 후보에 섞이면 목록만 길어지고 고르기 어려워진다.
     * 더 줄이지 않는 건 GPS 오차(옥외 10~30m, 실내는 그 이상) 때문이다 — 좌표가 흔들려
     * 정작 자기가 들어와 있는 건물이 빠지면 저장 자체가 불가능해진다.
     */
    private static final int NEARBY_RADIUS_METERS = 100;
    /** 그룹당 가져올 개수 — 카카오 카테고리 검색의 size 상한이 15다. */
    private static final int NEARBY_GROUP_SIZE = 15;
    /**
     * 응답에 실을 상한. 워치가 5개씩 "더 보기"로 펼치는 재료라 넉넉히 주되,
     * 블루투스 프록시를 타는 워치 응답이 무한정 커지지 않게 자른다.
     */
    private static final int NEARBY_LIMIT = 30;

    /**
     * 그룹별 병렬 호출 전용 풀. 블로킹 HTTP를 공용 ForkJoin 풀에 태우면 다른 parallelStream
     * 사용처를 굶기므로 따로 둔다. 데몬 스레드라 JVM 종료를 막지 않는다.
     */
    private final java.util.concurrent.ExecutorService nearbyExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(6, runnable -> {
                Thread thread = new Thread(runnable, "kakao-nearby");
                thread.setDaemon(true);
                return thread;
            });

    private final KakaoLocalClient client;

    public KakaoLocalGeocoder(KakaoLocalClient client) {
        this.client = client;
    }

    /**
     * 주소 검색으로 좌표를 찾고, 0건이면 키워드 검색으로 폴백한다. 카카오 주소 검색은
     * 도로명·지번 형식에 엄격해서 본문에서 뽑은 문자열이 그대로 통하지 않는 경우가 잦다.
     *
     * <p>폴백 때문에 이 호출은 <b>최대 2요청</b>이 될 수 있다.
     */
    @Override
    public Optional<ResolvedLocation> forwardAddress(String address) {
        if (isBlank(address)) {
            return Optional.empty();
        }
        Optional<ResolvedLocation> found = search(ADDRESS_SEARCH, address);
        return found.isPresent() ? found : search(KEYWORD_SEARCH, address);
    }

    @Override
    public Optional<ResolvedLocation> forwardKeyword(String keyword) {
        return isBlank(keyword) ? Optional.empty() : search(KEYWORD_SEARCH, keyword);
    }

    @Override
    public Optional<String> reverse(GeoPoint point) {
        if (point == null) {
            return Optional.empty();
        }
        JsonNode documents = call(COORD_TO_ADDRESS, Map.of(
                "x", String.valueOf(point.lng()),
                "y", String.valueOf(point.lat()),
                // 기본값이지만 명시해 둔다 — 좌표계를 헷갈리면 핀이 엉뚱한 곳에 간다.
                "input_coord", "WGS84"));
        if (documents == null || documents.isEmpty()) {
            // 바다·국외 좌표면 정상적으로 0건이다. 좌표는 살리고 주소만 비운다.
            return Optional.empty();
        }
        return Optional.ofNullable(addressNameOf(documents.get(0)));
    }

    /**
     * 좌표 주변 장소 후보 — 반경 {@value NEARBY_RADIUS_METERS}m 안에서 카테고리 그룹별로
     * 한 요청씩 병렬로 모아 거리순 상위 {@value NEARBY_LIMIT}개. 같은 장소가 두 그룹에
     * 걸리는 일은 없으므로(그룹이 배타적) 중복 제거는 하지 않는다. 실패한 그룹은
     * {@link #call}이 null로 흡수하므로 건너뛴다.
     */
    @Override
    public NearbySearch nearby(GeoPoint point, boolean expand) {
        if (point == null) {
            return new NearbySearch(java.util.List.of(), true);
        }
        if (expand) {
            return new NearbySearch(sortAndTrim(fetchGroups(NEARBY_ALL_GROUPS, point)), true);
        }
        java.util.List<NearbyPlace> found = fetchGroups(NEARBY_PRIMARY_GROUPS, point);
        if (!found.isEmpty()) {
            return new NearbySearch(sortAndTrim(found), false);
        }
        // 음식점·카페가 0건 — 나머지 그룹만 더 묻는다(방금 0건인 둘을 다시 물을 이유가 없다)
        return new NearbySearch(sortAndTrim(fetchGroups(NEARBY_SECONDARY_GROUPS, point)), true);
    }

    private java.util.List<NearbyPlace> fetchGroups(java.util.List<String> groups, GeoPoint point) {
        java.util.List<java.util.concurrent.CompletableFuture<JsonNode>> futures =
                groups.stream()
                        .map(group -> java.util.concurrent.CompletableFuture.supplyAsync(
                                () -> call(CATEGORY_SEARCH, Map.of(
                                        "category_group_code", group,
                                        "x", String.valueOf(point.lng()),
                                        "y", String.valueOf(point.lat()),
                                        "radius", String.valueOf(NEARBY_RADIUS_METERS),
                                        "sort", "distance",
                                        "size", String.valueOf(NEARBY_GROUP_SIZE))),
                                nearbyExecutor))
                        .toList();
        java.util.List<NearbyPlace> found = new java.util.ArrayList<>();
        for (java.util.concurrent.CompletableFuture<JsonNode> future : futures) {
            JsonNode documents = future.join();
            if (documents == null) continue;
            for (JsonNode document : documents) {
                toNearbyPlace(document).ifPresent(found::add);
            }
        }
        return found;
    }

    private java.util.List<NearbyPlace> sortAndTrim(java.util.List<NearbyPlace> found) {
        found.sort(java.util.Comparator.comparingInt(NearbyPlace::distanceMeters));
        return found.size() > NEARBY_LIMIT ? found.subList(0, NEARBY_LIMIT) : found;
    }

    private Optional<NearbyPlace> toNearbyPlace(JsonNode document) {
        String name = text(document.path("place_name"));
        if (name == null) {
            return Optional.empty();
        }
        // x=경도, y=위도, distance 는 x·y 를 준 요청에서만 오는 미터 문자열
        return GeoPoint.parse(document.path("y").asText(null), document.path("x").asText(null))
                .map(placePoint -> new NearbyPlace(
                        name,
                        text(document.path("category_name")),
                        document.path("distance").asInt(0),
                        placePoint,
                        addressNameOf(document),
                        // 카카오맵 장소 페이지 — 아이템에 실어 두면 상세에서 카카오맵으로 이어진다
                        text(document.path("place_url"))));
    }

    private Optional<ResolvedLocation> search(String path, String query) {
        JsonNode documents = call(path, Map.of("query", query, "size", "1"));
        if (documents == null || documents.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = documents.get(0);
        // x=경도, y=위도. 둘 다 문자열이다.
        return GeoPoint.parse(first.path("y").asText(null), first.path("x").asText(null))
                .map(point -> new ResolvedLocation(point, addressNameOf(first)));
    }

    /** 어떤 실패도 흡수한다 — Geocoder 계약이 "예외를 던지지 않는다"이기 때문이다. */
    private JsonNode call(String path, Map<String, String> params) {
        try {
            return client.documents(path, params);
        } catch (Exception e) {
            log.info("카카오 로컬 API 호출 실패(무시): path={}, cause={}", path, e.toString());
            return null;
        }
    }

    /** 도로명 → 지번 → 최상위 순으로 사람이 읽는 주소를 고른다. */
    private String addressNameOf(JsonNode document) {
        String roadAddress = text(document.path("road_address").path("address_name"));
        if (roadAddress != null) {
            return roadAddress;
        }
        // 키워드 검색 결과는 road_address가 객체가 아니라 road_address_name 문자열로 온다.
        String roadAddressName = text(document.path("road_address_name"));
        if (roadAddressName != null) {
            return roadAddressName;
        }
        String jibun = text(document.path("address").path("address_name"));
        return jibun != null ? jibun : text(document.path("address_name"));
    }

    private String text(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
