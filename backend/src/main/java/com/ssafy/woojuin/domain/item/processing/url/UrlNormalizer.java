package com.ssafy.woojuin.domain.item.processing.url;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 저장된 URL을 스크래핑하기 좋은 형태로 정규화한다.
 *
 * <ul>
 *   <li>추적 파라미터(utm_*, fbclid, gclid 등) 제거 — 같은 문서가 다른 URL로 저장되는 걸 줄이고
 *       oEmbed 매칭 정확도를 높인다</li>
 *   <li>youtu.be 단축 → youtube.com/watch?v= (oEmbed 제공자 매칭)</li>
 *   <li>모바일 호스트(m.example.com 등) → 데스크톱 호스트 (OG 태그가 더 잘 붙는다)</li>
 *   <li><b>네이버 블로그는 예외로 모바일 호스트를 쓴다</b> — 아래 참고</li>
 *   <li>지도 앱링크·장소 링크(카카오톡 {@code kko.to}, 네이버 {@code naver.me}가 풀리는 곳)
 *       → 장소 정보가 실제로 있는 페이지</li>
 * </ul>
 *
 * <p><b>지도 앱링크 특례.</b> 카카오톡·네이버 앱에서 공유한 지도 링크는 앱 설치를 유도하는
 * 랜딩 페이지로 풀린다. 그 페이지에는 장소 정보가 하나도 없다 — 카카오
 * {@code applink.map.kakao.com/place?id=}는 og:title이 "카카오맵"인 안내 페이지(robots
 * noindex)이고, 네이버 {@code map.naver.com/p/entry/place/{id}}는 2.3KB SPA 껍데기다.
 * 장소 페이지({@code place.map.kakao.com/{id}}, {@code m.place.naver.com/place/{id}})로 보내면
 * 이름·주소·좌표가 다 나온다.
 *
 * <p>단축 링크는 정규화 시점엔 불투명한 토큰이라 이 규칙이 바로 걸리지 않는다. 리다이렉트가
 * 끝난 뒤 {@code UrlItemProcessor}가 최종 URL을 다시 정규화해서 한 번 더 받아온다.
 *
 * <p><b>네이버 블로그 특례.</b> 데스크톱 포스트 URL({@code blog.naver.com/{id}/{logNo}})은
 * 2.8KB짜리 iframe 껍데기라서 <b>본문도 OG 태그도 없다</b>. {@code <title>}에 글 제목이 아니라
 * 블로그 이름("○○○ : 네이버 블로그")만 있어서, 미리보기 제목이 전부 블로그 이름으로 저장되고
 * 트랙 B는 통째로 실패했다. iframe을 따라갈 수도 없다 — {@code src}가 HTML에 없고 JS가 런타임에
 * 채운다.
 *
 * <p>실측 비교(같은 글, 우리 fetcher와 같은 UA):
 * <pre>
 *   blog.naver.com/{id}/{logNo}      2.8KB   본문 없음, OG 없음
 *   m.blog.naver.com/{id}/{logNo}    200KB   본문 1617자, OG·썸네일 있음   ← 선택
 *   blog.naver.com/PostView.naver…   336KB   본문 1997자, OG·썸네일 있음
 * </pre>
 *
 * <p>본문은 PostView가 조금 더 많이 나오지만 모바일을 택했다 — 사람이 열 수 있는 공개
 * 페이지라 내부 엔드포인트보다 덜 변하고, HTML이 40% 작아서 fetch 상한·지연에 유리하다.</p>
 *
 * <p>정규화가 불가능하거나 파싱이 실패하면 <b>원본을 그대로 반환</b>한다 — 정규화는
 * 최선 노력일 뿐, 여기서 아이템 처리를 실패시키면 안 된다.
 */
@Component
public class UrlNormalizer {

    private static final List<String> TRACKING_PREFIXES = List.of("utm_");
    private static final List<String> TRACKING_KEYS = List.of(
            "fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "spm", "ref", "ref_src");

    private static final Set<String> NAVER_BLOG_HOSTS =
            Set.of("blog.naver.com", "m.blog.naver.com");

    /** 카카오톡 공유(kko.to)가 풀리는 앱링크 랜딩 호스트. 장소 페이지로 보낸다 — 아래 javadoc 참고. */
    private static final String KAKAO_APPLINK_HOST = "applink.map.kakao.com";
    private static final String KAKAO_PLACE_HOST = "place.map.kakao.com";
    private static final Pattern KAKAO_PLACE_ID = Pattern.compile("\\d{1,20}");

    /** 네이버 장소를 가리키는 호스트들. 전부 모바일 장소 페이지로 모은다 — 아래 javadoc 참고. */
    private static final Set<String> NAVER_PLACE_HOSTS =
            Set.of("map.naver.com", "m.place.naver.com", "place.naver.com");
    private static final String NAVER_PLACE_MOBILE_HOST = "m.place.naver.com";
    /** 경로에서 장소 id를 찾는다 — /p/entry/place/{id}, /place/{id}, /restaurant/{id}/home 등. */
    private static final Pattern NAVER_PLACE_ID = Pattern.compile("/(\\d{6,20})(?:/|$)");
    private static final String NAVER_BLOG_MOBILE_HOST = "m.blog.naver.com";
    /** 사용자 URL에서 뽑은 값으로 새 URL을 조립하므로 형태를 검증한다(경로 주입 방지). */
    private static final Pattern NAVER_BLOG_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern NAVER_LOG_NO = Pattern.compile("\\d{1,30}");

    public String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            uri = rewriteHostSpecific(uri);
            String cleanedQuery = stripTracking(uri.getQuery());
            URI result = new URI(
                    uri.getScheme(), uri.getAuthority(), uri.getPath(),
                    cleanedQuery, null);   // fragment 제거 (#앵커는 문서 동일)
            return result.toString();
        } catch (URISyntaxException e) {
            return rawUrl;
        }
    }

    /**
     * 정규화가 <b>다른 호스트</b>의 URL을 가리키면 그 URL을 준다. 단축 링크가 리다이렉트로
     * 풀린 뒤 "더 받아올 만한 곳이 있는가"를 판단하는 공용 기준이다 —
     * {@code UrlItemProcessor}(다시 받아올지)와 {@link FallbackHtmlFetcher}(브라우저를 굽지 않고
     * 넘길지)가 같은 기준을 써야 한쪽만 헛돌지 않는다.
     *
     * <p>문자열이 달라졌는지로 보지 않는 이유: {@link #normalize}는 추적 파라미터를 떼고
     * 퍼센트 인코딩된 경로를 디코딩하기도 해서, 같은 페이지인데도 문자열이 달라진다. 그걸
     * "더 나은 URL"로 취급하면 한국어 경로나 추적 파라미터가 붙은 URL을 전부 두 번 받아온다.
     * 실제 재작성 규칙(지도 앱링크·네이버 블로그·youtu.be·모바일 호스트)은 모두 호스트를
     * 바꾸므로 호스트 비교로 충분하다.
     *
     * @return 다른 호스트를 가리키는 정규화 URL. 같은 호스트거나 판별 불가면 empty
     */
    public Optional<String> betterUrlOnAnotherHost(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String renormalized = normalize(url);
        if (renormalized == null || renormalized.equals(url)) {
            return Optional.empty();
        }
        String from = hostOf(url);
        if (from.isEmpty() || from.equals(hostOf(renormalized))) {
            return Optional.empty();
        }
        return Optional.of(renormalized);
    }

    private String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /** 유튜브 단축·모바일 호스트·네이버 블로그처럼 호스트별로 형태가 정해진 케이스를 바로잡는다. */
    private URI rewriteHostSpecific(URI uri) throws URISyntaxException {
        String host = uri.getHost();
        if (host == null) {
            return uri;
        }
        host = host.toLowerCase(Locale.ROOT);

        // youtu.be/{id} → youtube.com/watch?v={id}
        if (host.equals("youtu.be")) {
            String id = trimLeadingSlash(uri.getPath());
            if (!id.isBlank()) {
                return new URI("https", "www.youtube.com", "/watch", "v=" + id, null);
            }
        }

        // 카카오톡 공유(kko.to)가 풀리는 앱링크 랜딩 페이지 → 장소 페이지.
        // 랜딩 페이지는 og:title이 "카카오맵"인 일반 안내 페이지라(robots noindex) 장소 이름도
        // 주소도 좌표도 없다. 장소 페이지는 같은 장소의 스태틱맵 좌표와 이름·주소를 담는다.
        if (host.equals(KAKAO_APPLINK_HOST) && uri.getPath() != null
                && uri.getPath().startsWith("/place")) {
            String placeId = queryValue(uri.getQuery(), "id");
            if (placeId != null && KAKAO_PLACE_ID.matcher(placeId).matches()) {
                return new URI("https", KAKAO_PLACE_HOST, "/" + placeId, null, null);
            }
            return uri;
        }

        // 네이버 장소(지도 링크·플레이스) → 모바일 장소 페이지. 아래 m.* 제거 규칙보다 먼저다.
        if (NAVER_PLACE_HOSTS.contains(host)) {
            URI mobilePlace = naverMobilePlace(uri);
            return mobilePlace != null ? mobilePlace : uri;
        }

        // 네이버 블로그(데스크톱·모바일·PostView 어느 형태로 저장했든) → 모바일 포스트 URL.
        // 클래스 javadoc의 실측 표 참고. 아래 m.* 제거 규칙보다 먼저 반환해야 한다 —
        // 그러지 않으면 모바일 링크가 본문 없는 데스크톱 껍데기로 되돌아간다.
        if (NAVER_BLOG_HOSTS.contains(host)) {
            URI mobilePost = naverBlogMobilePost(uri);
            // 글 단위로 특정하지 못하면(블로그 홈 등) 손대지 않는다. m.도 떼지 않는다 —
            // 데스크톱 블로그 홈 역시 iframe 껍데기라 바꿔서 나아질 게 없다.
            return mobilePost != null ? mobilePost : uri;
        }

        // m.* 모바일 호스트 → 데스크톱 호스트
        if (host.startsWith("m.")) {
            return new URI(uri.getScheme(), uri.getUserInfo(), host.substring(2),
                    uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        }

        return uri;
    }

    /**
     * 네이버 장소 URL에서 장소 id를 찾아 모바일 장소 페이지로 만든다.
     *
     * <p>지도 링크({@code map.naver.com/p/entry/place/{id}})는 URL에 좌표가 없고 페이지도
     * 2.3KB SPA 껍데기라서 <b>미리보기도 좌표도 얻을 수 없다</b>. 모바일 장소 페이지는 같은 장소를
     * 580KB로 주면서 og:title·썸네일과 '길찾기' 링크(좌표 포함)를 함께 담는다.
     *
     * @return 모바일 장소 URI. 장소 id를 못 찾으면(지도 검색 화면 등) null
     */
    private URI naverMobilePlace(URI uri) throws URISyntaxException {
        String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher id = NAVER_PLACE_ID.matcher(path);
        if (!id.find()) {
            return null;
        }
        return new URI("https", NAVER_PLACE_MOBILE_HOST, "/place/" + id.group(1), null, null);
    }

    /**
     * 네이버 블로그 URL에서 글을 특정해 모바일 포스트 URL로 만든다. 세 형태를 받는다 —
     * {@code /{blogId}/{logNo}}, {@code /PostView.naver?blogId=&logNo=}, 그리고 두 형태의
     * 모바일 버전.
     *
     * @return 모바일 포스트 URI. 글을 특정할 수 없으면(블로그 홈, 형태 불일치) null
     */
    private URI naverBlogMobilePost(URI uri) throws URISyntaxException {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String blogId;
        String logNo;

        if (path.contains("PostView")) {
            blogId = queryValue(uri.getQuery(), "blogId");
            logNo = queryValue(uri.getQuery(), "logNo");
        } else {
            String[] segments = trimLeadingSlash(path).split("/");
            if (segments.length < 2) {
                return null;   // /{blogId} 만 있는 블로그 홈
            }
            blogId = segments[0];
            logNo = segments[1];
        }

        if (blogId == null || logNo == null
                || !NAVER_BLOG_ID.matcher(blogId).matches()
                || !NAVER_LOG_NO.matcher(logNo).matches()) {
            return null;
        }
        // 쿼리·fragment는 버린다 — 글을 특정하는 데 blogId/logNo 외에는 필요하지 않다.
        return new URI("https", NAVER_BLOG_MOBILE_HOST, "/" + blogId + "/" + logNo, null, null);
    }

    private String stripTracking(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        for (String param : query.split("&")) {
            String key = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
            String lower = key.toLowerCase(Locale.ROOT);
            boolean tracking = TRACKING_KEYS.contains(lower)
                    || TRACKING_PREFIXES.stream().anyMatch(lower::startsWith);
            if (!tracking) {
                kept.add(param);
            }
        }
        return kept.isEmpty() ? null : String.join("&", kept);
    }

    private String queryValue(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && param.substring(0, eq).equalsIgnoreCase(key)) {
                return param.substring(eq + 1);
            }
        }
        return null;
    }

    private String trimLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
