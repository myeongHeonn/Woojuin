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

    private enum Provider { KAKAO, NAVER, GOOGLE }

    private record NamedPattern(String name, Pattern regex) {
    }

    private static final List<NamedPattern> GOOGLE_PATTERNS = List.of(
            // data= 안의 !3d!4d — 장소 핀의 실제 좌표다. 아래 뷰포트보다 반드시 먼저 본다.
            new NamedPattern("google-place", Pattern.compile("!3d" + COORD + "!4d" + COORD)),
            // Maps URLs API: /maps/search/?api=1&query=lat,lng (%2C 인코딩도 허용)
            new NamedPattern("google-query",
                    Pattern.compile("[?&](?:q|query|ll|center)=" + COORD + "(?:,|%2[Cc])" + COORD)),
            // /@lat,lng,17z — 지도 화면 중심이지 핀 좌표가 아니라 마지막 순위다.
            new NamedPattern("google-viewport", Pattern.compile("/@" + COORD + "," + COORD)));

    private static final List<NamedPattern> KAKAO_PATTERNS = List.of(
            // /link/map/{이름},{lat},{lng} 와 /link/to/{이름},{lat},{lng}
            // [^?#]* 를 탐욕적으로 두어 이름에 쉼표가 있어도 마지막 두 숫자를 잡는다.
            // /link/map/{placeId} 처럼 숫자 하나만 있는 형태는 쉼표가 없어 매칭되지 않는다.
            new NamedPattern("kakao-link",
                    Pattern.compile("/link/(?:map|to)/[^?#]*," + COORD + "," + COORD)));

    /** 이름 있는 파라미터는 순서가 고정이 아니라 위도·경도를 따로 찾는다. x=경도, y=위도. */
    private static final Pattern LAT_PARAM = Pattern.compile("[?&](?:lat|y)=" + COORD);
    private static final Pattern LNG_PARAM = Pattern.compile("[?&](?:lng|lon|x)=" + COORD);

    /**
     * 후보 URL들을 순서대로 보고 첫 성공에서 멈춘다.
     *
     * @param candidateUrls 원본 URL, 정규화된 URL, 리다이렉트 해소된 최종 URL 등 (null 허용)
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

        for (NamedPattern pattern : patternsFor(provider)) {
            Matcher matcher = pattern.regex().matcher(url);
            if (matcher.find()) {
                Optional<GeoPoint> point = GeoPoint.parse(matcher.group(1), matcher.group(2));
                if (point.isPresent() && acceptable(provider, point.get(), pattern.name(), url)) {
                    log.debug("지도 링크에서 좌표 추출: pattern={}, lat={}, lng={}",
                            pattern.name(), point.get().lat(), point.get().lng());
                    return point;
                }
            }
        }
        return parseNamedParams(provider, url);
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
