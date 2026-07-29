package com.ssafy.woojuin.domain.location;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지도 공유 링크의 URL 문자열에서 좌표를 직접 뽑는다 (FR-023). 순수 함수 — 네트워크를
 * 타지 않으므로 위치 확보 경로 중 가장 싸고 정확하다.
 *
 * <p><b>지도 호스트일 때만</b> 패턴을 적용한다. {@code ?q=37.5,127.0} 같은 형태는 아무
 * 사이트에나 나올 수 있어서, 호스트 게이트 없이는 엉뚱한 핀이 대량으로 생긴다.
 *
 * <p>여러 후보 URL을 받는 이유: {@code UrlNormalizer.normalize}가 fragment를 버리고
 * {@code ref} 같은 파라미터를 지우기 때문에(UrlNormalizer 참고) <b>원본 URL도 함께</b>
 * 넣어야 한다. 단축 링크는 리다이렉트가 끝난 최종 URL(jsoup {@code Document.location()})이
 * 후보로 들어온다 — 그 fetch는 홉마다 SSRF 검사를 통과한 경로다.
 *
 * <p>후보에는 <b>페이지가 실어준 지도 이미지 URL</b>(og:image / twitter:image)도 들어온다.
 * 카카오 장소 페이지({@code place.map.kakao.com/{id}})는 URL에 좌표가 없지만 미리보기용
 * 스태틱맵 이미지 URL에 정확한 좌표를 담고 있어서, <b>이미 받아온 HTML만으로</b> 좌표를
 * 얻을 수 있다(추가 네트워크 0회). 카카오맵 앱의 '공유'가 주는 링크가 이 형태라 실사용
 * 빈도가 가장 높은 경로다.
 *
 * <p>투영 좌표(카카오 {@code ?urlX=507000&urlY=1120000} WCONGNAMUL)는 정수라서 모든
 * 패턴이 소수점을 요구하는 것만으로 자동으로 걸러진다. 그대로 저장하면 핀이 바다에 꽂힌다.
 * 변환이 필요해지면 카카오 {@code /v2/local/geo/transcoord.json}을 쓸 수 있지만 지금은
 * 지원하지 않는다.
 */
@Slf4j
@Component
public class MapLinkCoordinateParser {

    /** 좌표는 소수점을 반드시 포함해야 한다 — 투영 좌표·장소 id 같은 정수를 배제한다. */
    private static final String COORD = "(-?\\d{1,3}\\.\\d+)";

    private enum Provider { KAKAO, KAKAO_STATICMAP, NAVER, NAVER_STATICMAP, GOOGLE }

    /**
     * @param lngFirst 경도가 먼저 나오는 패턴인지. 대부분은 위도가 먼저지만 카카오 스태틱맵의
     *                 {@code m=} 은 {@code 경도,위도} 순이다 — 뒤바꿔 읽으면 핀이 중국 어딘가에
     *                 꽂히므로 패턴마다 명시한다
     */
    private record NamedPattern(String name, Pattern regex, boolean lngFirst) {

        private NamedPattern(String name, Pattern regex) {
            this(name, regex, false);
        }
    }

    private static final List<NamedPattern> GOOGLE_PATTERNS = List.of(
            // data= 안의 !3d!4d — 장소 핀의 실제 좌표다. 아래 뷰포트보다 반드시 먼저 본다.
            // 구글 지도 앱의 '공유'(maps.app.goo.gl)가 리다이렉트 끝에 주는 형태가 이것이다.
            new NamedPattern("google-place", Pattern.compile("!3d" + COORD + "!4d" + COORD)),
            // Maps URLs API: /maps/search/?api=1&query=lat,lng (%2C 인코딩도 허용)
            // destination/daddr 은 길찾기 링크의 도착지다. saddr(출발지)는 일부러 제외했다 —
            // 사용자가 저장하려는 장소는 도착지이고, 출발지를 잡으면 엉뚱한 핀이 된다.
            new NamedPattern("google-query", Pattern.compile(
                    "[?&](?:q|query|ll|center|destination|daddr)=" + COORD + "(?:,|%2[Cc])" + COORD)),
            // /@lat,lng,17z — 지도 화면 중심이지 핀 좌표가 아니라 마지막 순위다.
            new NamedPattern("google-viewport", Pattern.compile("/@" + COORD + "," + COORD)));

    private static final List<NamedPattern> KAKAO_PATTERNS = List.of(
            // /link/map/{이름},{lat},{lng} 와 /link/to/{이름},{lat},{lng}
            // [^?#]* 를 탐욕적으로 두어 이름에 쉼표가 있어도 마지막 두 숫자를 잡는다.
            // /link/map/{placeId} 처럼 숫자 하나만 있는 형태는 쉼표가 없어 매칭되지 않는다.
            new NamedPattern("kakao-link",
                    Pattern.compile("/link/(?:map|to)/[^?#]*," + COORD + "," + COORD)));

    /**
     * 카카오 장소 페이지가 미리보기 이미지로 싣는 스태틱맵. {@code m=경도,위도} 순이다.
     *
     * <p>실측으로 확인했다 — 서울시청·부산역의 장소 페이지에서 뽑은 값이 카카오 로컬 API가
     * 주는 좌표와 소수점 10자리까지 일치했다. 즉 <b>요청자 위치가 아니라 그 장소의 좌표</b>다
     * (구글은 정반대다 — {@link #detectProvider} 참고).
     */
    private static final List<NamedPattern> KAKAO_STATICMAP_PATTERNS = List.of(
            new NamedPattern("kakao-staticmap",
                    Pattern.compile("[?&]m=" + COORD + "(?:,|%2[Cc])" + COORD), true));

    /**
     * 네이버 스마트에디터가 글 본문에 심는 정적 지도 이미지. {@code markers=…pos:경도 위도…}
     * 형태이고 값은 퍼센트 인코딩돼 있다({@code pos%3A126.82%2035.19}).
     *
     * <p>블로그 글쓴이가 <b>직접 찍은 핀</b>이라 본문 주소를 지오코딩하는 것보다 정확하다 —
     * 같은 글에서 두 방식을 비교했을 때 26m 차이가 났고 임베드 쪽이 실제 가게 위치였다.
     */
    private static final List<NamedPattern> NAVER_STATICMAP_PATTERNS = List.of(
            // 구분자로 리터럴 공백은 받지 않는다 — URI.create가 애초에 거부하므로 도달할 수 없다.
            new NamedPattern("naver-staticmap", Pattern.compile(
                    "pos(?::|%3[Aa])" + COORD + "(?:%20|\\+)" + COORD), true));

    /**
     * 카카오 스태틱맵 좌표계 표기. 이게 없으면 좌표를 쓰지 않는다 — 카카오가 이 파라미터를
     * WCONGNAMUL 같은 투영 좌표계로 바꾸는 날, 핀을 바다에 꽂는 대신 조용히 포기한다.
     */
    private static final Pattern WGS84_MARKER =
            Pattern.compile("[?&]srs=wgs84", Pattern.CASE_INSENSITIVE);

    /**
     * 네이버 스태틱맵의 좌표계 파라미터. 없으면 기본값이 WGS84(EPSG:4326)라서 그대로 쓰고,
     * <b>4326이 아닌 값이 명시돼 있으면 거부한다</b> — 카카오의 {@code srs} 검사와 같은 이유로
     * fail-closed다.
     */
    private static final Pattern NAVER_CRS_PARAM =
            Pattern.compile("[?&]crs=([^&]+)", Pattern.CASE_INSENSITIVE);

    /** 이름 있는 파라미터는 순서가 고정이 아니라 위도·경도를 따로 찾는다. x=경도, y=위도. */
    private static final Pattern LAT_PARAM = Pattern.compile("[?&](?:lat|y)=" + COORD);
    private static final Pattern LNG_PARAM = Pattern.compile("[?&](?:lng|lon|x)=" + COORD);

    /**
     * 후보 URL들을 순서대로 보고 첫 성공에서 멈춘다.
     *
     * @param candidateUrls 원본 URL, 정규화된 URL, 리다이렉트 해소된 최종 URL, 그리고 페이지가
     *                      실어준 지도 이미지 URL(og:image / twitter:image) 등 (null 허용).
     *                      <b>실제 링크를 앞에, 페이지 에셋을 뒤에</b> 두면 우선순위가 맞는다 —
     *                      링크에 박힌 좌표가 페이지 미리보기 이미지보다 정확하다
     */
    public Optional<GeoPoint> parse(String... candidateUrls) {
        if (candidateUrls == null) {
            return Optional.empty();
        }
        for (String url : candidateUrls) {
            Optional<GeoPoint> found = parseOne(url);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<GeoPoint> parseOne(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Provider provider = detectProvider(url);
        if (provider == null) {
            return Optional.empty();
        }
        if (!coordinateSystemTrusted(provider, url)) {
            return Optional.empty();
        }

        for (NamedPattern pattern : patternsFor(provider)) {
            Matcher matcher = pattern.regex().matcher(url);
            if (matcher.find()) {
                String latText = matcher.group(pattern.lngFirst() ? 2 : 1);
                String lngText = matcher.group(pattern.lngFirst() ? 1 : 2);
                Optional<GeoPoint> point = GeoPoint.parse(latText, lngText);
                if (point.isPresent() && acceptable(provider, point.get(), pattern.name(), url)) {
                    log.debug("지도 링크에서 좌표 추출: pattern={}, lat={}, lng={}",
                            pattern.name(), point.get().lat(), point.get().lng());
                    return point;
                }
            }
        }
        // 스태틱맵은 전용 패턴만 신뢰한다. lat/x 같은 이름 있는 파라미터를 함께 훑으면
        // 이미지 크기·줌 같은 무관한 값을 좌표로 오독할 여지가 생긴다.
        if (isStaticMap(provider)) {
            return Optional.empty();
        }
        return parseNamedParams(provider, url);
    }

    private boolean isStaticMap(Provider provider) {
        return provider == Provider.KAKAO_STATICMAP || provider == Provider.NAVER_STATICMAP;
    }

    /**
     * 스태틱맵 URL의 좌표계를 신뢰할 수 있는지 본다. 두 제공자가 방식이 다르다 — 카카오는
     * {@code srs=wgs84}를 명시하므로 그 표기를 요구하고, 네이버는 생략 시 기본값이 WGS84라서
     * {@code crs}가 있을 때만 4326인지 확인한다. 둘 다 <b>의심스러우면 포기</b>한다.
     */
    private boolean coordinateSystemTrusted(Provider provider, String url) {
        if (provider == Provider.KAKAO_STATICMAP && !WGS84_MARKER.matcher(url).find()) {
            log.debug("카카오 스태틱맵에 srs=wgs84 표기가 없어 좌표를 쓰지 않는다: url={}", url);
            return false;
        }
        if (provider == Provider.NAVER_STATICMAP) {
            Matcher crs = NAVER_CRS_PARAM.matcher(url);
            if (crs.find() && !crs.group(1).equalsIgnoreCase("epsg:4326")
                    && !crs.group(1).equalsIgnoreCase("epsg%3A4326")) {
                log.debug("네이버 스태틱맵 좌표계가 WGS84가 아니라 좌표를 쓰지 않는다: crs={}, url={}",
                        crs.group(1), url);
                return false;
            }
        }
        return true;
    }

    private Optional<GeoPoint> parseNamedParams(Provider provider, String url) {
        Matcher lat = LAT_PARAM.matcher(url);
        Matcher lng = LNG_PARAM.matcher(url);
        if (!lat.find() || !lng.find()) {
            return Optional.empty();
        }
        Optional<GeoPoint> point = GeoPoint.parse(lat.group(1), lng.group(1));
        if (point.isPresent() && acceptable(provider, point.get(), "named-params", url)) {
            log.debug("지도 링크 파라미터에서 좌표 추출: lat={}, lng={}",
                    point.get().lat(), point.get().lng());
            return point;
        }
        return Optional.empty();
    }

    /**
     * 국내 제공자가 국외 좌표를 주는 일은 사실상 없다. 그런 값이 나오면 lat/lng를 뒤바꿔
     * 읽었거나 패턴이 엉뚱한 숫자를 잡은 것이므로 <b>거부하고 크게 로그를 남긴다</b>.
     *
     * <p>자동으로 lat/lng를 뒤바꾸지 않는 이유: 스왑하면 파서 버그와 정상 동작이 구별되지
     * 않아 문제가 영구히 숨는다. 게다가 정당한 해외 구글 링크를 망친다.
     */
    private boolean acceptable(Provider provider, GeoPoint point, String pattern, String url) {
        if (provider == Provider.GOOGLE || point.isInKorea()) {
            return true;
        }
        log.warn("국내 지도 링크에서 국외 좌표가 나왔다 — 패턴 오류 가능성, 무시한다. "
                + "provider={}, pattern={}, lat={}, lng={}, url={}",
                provider, pattern, point.lat(), point.lng(), url);
        return false;
    }

    private List<NamedPattern> patternsFor(Provider provider) {
        return switch (provider) {
            case GOOGLE -> GOOGLE_PATTERNS;
            case KAKAO -> KAKAO_PATTERNS;
            case KAKAO_STATICMAP -> KAKAO_STATICMAP_PATTERNS;
            case NAVER_STATICMAP -> NAVER_STATICMAP_PATTERNS;
            case NAVER -> List.of();   // 네이버는 이름 있는 파라미터만 신뢰한다 (아래 주석)
        };
    }

    /**
     * 지도 서비스 URL인지 판별한다. 지도가 아니면 아예 패턴을 돌리지 않는다.
     *
     * <p>단축 링크 호스트(naver.me, kko.kr, maps.app.goo.gl)는 자체적으로 좌표를 갖지
     * 않지만, 리다이렉트 해소된 URL이 후보로 함께 들어오므로 실질적으로 커버된다.
     *
     * <p>네이버 {@code /p/entry/place/{id}?c=...} 의 {@code c=} 튜플은 <b>지원하지 않는다</b>.
     * 포맷이 버전마다 달라(구 v5는 경도가 먼저) 실제 공유 링크로 실측하지 않으면 위도·경도를
     * 뒤바꿔 저장할 위험이 크다. 이름 있는 파라미터(lat/lng, x/y)만 신뢰한다.
     *
     * <p><b>구글 스태틱맵({@code /maps/api/staticmap})은 반드시 거부한다.</b> 구글 지도
     * 페이지의 og:image·twitter:image가 이 URL을 싣는데, 그 {@code center=} 값은 장소가 아니라
     * <b>요청자 IP의 위치</b>다 — 서울시청·부산역·에펠탑 페이지를 각각 요청했을 때 셋 다 동일한
     * 좌표(요청한 사무실 위치)가 돌아오는 것을 실측으로 확인했다. 거부하지 않으면 모든 구글
     * 지도 링크가 서버 데이터센터에 핀을 꽂으면서 그럴듯해 보이기까지 한다.
     *
     * <p>{@code share.google} 단축 링크도 지원할 수 없다. 리다이렉트 끝이 JS 전용 인터스티셜
     * ({@code http-equiv=refresh} → {@code /httpservice/retry/enablejs})이라 좌표도 장소 정보도
     * HTML에 없다. 구글 지도 <i>앱</i>의 '공유'가 주는 {@code maps.app.goo.gl} 링크는 최종 URL에
     * {@code !3d!4d}가 들어와서 정상 동작한다.
     */
    private Provider detectProvider(String url) {
        String host;
        String path;
        try {
            URI uri = URI.create(url.trim());
            host = uri.getHost();
            path = uri.getPath() == null ? "" : uri.getPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);

        // 위 javadoc 참고 — IP 기반 좌표라 신뢰할 수 없다. 구글 판별보다 먼저 걸러낸다.
        if (host.equals("maps.googleapis.com") || path.startsWith("/maps/api/staticmap")) {
            log.debug("구글 스태틱맵은 요청자 IP 기준 좌표라 무시한다: url={}", url);
            return null;
        }
        // staticmap.kakao.com 은 아래 map.kakao.com 검사에도 걸리므로(endsWith) 반드시 먼저 본다.
        if (host.equals("staticmap.kakao.com")) {
            return Provider.KAKAO_STATICMAP;
        }
        // 네이버 스마트에디터가 글 본문에 심는 정적 지도 이미지. 경로까지 확인해서
        // 같은 CDN이 서비스하는 다른 이미지가 걸리지 않게 한다.
        if (host.endsWith("pstatic.net") && path.startsWith("/static.map")) {
            return Provider.NAVER_STATICMAP;
        }
        if (host.endsWith("map.kakao.com") || host.equals("kko.kr")) {
            return Provider.KAKAO;
        }
        if (host.endsWith("map.naver.com") || host.endsWith("place.naver.com")
                || host.equals("naver.me")) {
            return Provider.NAVER;
        }
        if (host.equals("maps.google.com") || host.endsWith("goo.gl")
                || (host.endsWith("google.com") && path.startsWith("/maps"))
                || (host.endsWith("google.co.kr") && path.startsWith("/maps"))) {
            return Provider.GOOGLE;
        }
        return null;
    }
}
