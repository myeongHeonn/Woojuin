package com.ssafy.woojuin.domain.item.processing.url;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 미리보기 대표 이미지(og:image·oEmbed 썸네일)의 원본 바이트를 받아온다. 목록 카드용
 * 저용량 webp 썸네일을 만들어 S3에 캐시하기 위한 입력이다.
 *
 * <p>크롤링한 페이지가 준 URL을 서버가 직접 받아오므로 {@link JsoupHtmlFetcher}와 같은
 * SSRF 벡터다 — 같은 방식으로 자동 리다이렉트를 끄고 홉마다 {@link PrivateNetworkGuard}로
 * 다시 검사한다(302로 내부 주소를 가리키는 우회 방어).
 *
 * <p>content-type이 이미지가 아니거나 크기 상한을 넘는 응답은 거부한다 — og:image 자리에
 * HTML 에러 페이지가 오거나 비정상적으로 큰 파일이 걸리는 경우다. 모든 실패는
 * {@link HtmlFetchException}으로 던지고, 호출부(UrlItemProcessor)가 흡수해 썸네일 없이
 * 외부 URL 폴백으로 진행한다 — 썸네일은 최적화지 필수 경로가 아니다.
 */
@Component
public class PreviewImageFetcher {

    /** HTML fetch와 동일한 UA — 봇을 숨기지 않되 실제 브라우저처럼 보여야 주는 호스트가 있다. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; WoojuinBot/1.0; +https://woojuin.app/bot)";

    private final PrivateNetworkGuard guard;
    private final int timeoutMs;
    private final int maxImageBytes;
    private final int maxRedirects;

    public PreviewImageFetcher(
            PrivateNetworkGuard guard,
            @Value("${woojuin.fetch.timeout-ms:5000}") int timeoutMs,
            @Value("${woojuin.fetch.max-image-bytes:10485760}") int maxImageBytes,
            @Value("${woojuin.fetch.max-redirects:5}") int maxRedirects) {
        this.guard = guard;
        this.timeoutMs = timeoutMs;
        this.maxImageBytes = maxImageBytes;
        this.maxRedirects = maxRedirects;
    }

    public byte[] fetch(String url) {
        String current = url;
        for (int hop = 0; hop <= maxRedirects; hop++) {
            URI uri = parse(current);
            guard.verifyAllowed(uri);

            Connection.Response response;
            try {
                response = Jsoup.connect(current)
                        .userAgent(USER_AGENT)
                        .timeout(timeoutMs)
                        .maxBodySize(maxImageBytes)
                        .followRedirects(false)   // 홉마다 직접 검사하려고 끈다
                        .ignoreHttpErrors(true)   // 4xx/5xx도 예외 대신 응답으로 받아 상태코드로 판단
                        .ignoreContentType(true)  // content-type 검증은 아래에서 직접
                        .execute();
            } catch (IOException e) {
                throw new HtmlFetchException("이미지 요청 실패: " + current, e);
            }

            int status = response.statusCode();
            if (isRedirect(status)) {
                String location = response.header("Location");
                if (location == null || location.isBlank()) {
                    throw new HtmlFetchException("리다이렉트에 Location 헤더가 없음: " + current);
                }
                current = uri.resolve(location).toString();   // 상대 Location 절대화
                continue;
            }
            if (status >= 400) {
                throw new HtmlFetchException("이미지 응답 오류: status=" + status + ", url=" + current);
            }

            String contentType = response.contentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new HtmlFetchException("이미지가 아닌 응답: contentType=" + contentType + ", url=" + current);
            }
            byte[] bytes = response.bodyAsBytes();
            // Jsoup은 maxBodySize에서 조용히 잘라버린다 — 잘린 이미지는 디코딩이 어차피
            // 실패하지만, 상한에 딱 걸린 응답은 잘렸을 가능성이 높으니 여기서 명시적으로 거부한다.
            if (bytes.length >= maxImageBytes) {
                throw new HtmlFetchException("이미지가 크기 상한(" + maxImageBytes + "B)을 초과: " + current);
            }
            return bytes;
        }
        throw new HtmlFetchException("리다이렉트 한도(" + maxRedirects + ") 초과: " + url);
    }

    private URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new HtmlFetchException("잘못된 URL: " + url, e);
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }
}
