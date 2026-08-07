package com.ssafy.woojuin.domain.item.processing.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * JDK HttpServer로 이미지 호스트를 흉내 내, 리다이렉트 추적·content-type 검증·크기 상한을
 * 실제 네트워크(로컬 루프백)로 검증한다. PrivateNetworkGuard는 루프백을 차단하므로 테스트
 * 전용으로 통과시키는 가드를 주입한다 — 가드 자체는 PrivateNetworkGuardTest가 검증한다.
 */
class PreviewImageFetcherTest {

    private static final int MAX_IMAGE_BYTES = 1024;

    private HttpServer server;
    private String baseUrl;

    /** 루프백(127.0.0.1)은 실 가드가 막으므로, 테스트에선 통과만 시키는 가드로 대체한다. */
    private final PrivateNetworkGuard allowAll = new PrivateNetworkGuard() {
        @Override
        public void verifyAllowed(URI uri) {
            // no-op
        }
    };

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private PreviewImageFetcher fetcher() {
        return new PreviewImageFetcher(allowAll, 2000, MAX_IMAGE_BYTES, 5);
    }

    private void serveImage(String path, byte[] bytes) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void 이미지_응답이면_바이트를_돌려준다() {
        byte[] image = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        serveImage("/og.png", image);

        byte[] result = fetcher().fetch(baseUrl + "/og.png");

        assertThat(result).isEqualTo(image);
    }

    @Test
    void 리다이렉트를_따라가서_최종_이미지를_받는다() {
        // CDN이 서명 URL로 302를 주는 흔한 경우 — 홉마다 가드를 다시 태운 뒤 따라간다.
        byte[] image = new byte[] {1, 2, 3};
        serveImage("/final.png", image);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/final.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        byte[] result = fetcher().fetch(baseUrl + "/redirect");

        assertThat(result).isEqualTo(image);
    }

    @Test
    void 이미지가_아닌_응답은_거부한다() {
        // og:image 자리에 HTML 에러 페이지가 오는 경우 — 썸네일 원료가 못 된다.
        server.createContext("/not-image", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            byte[] body = "<html>error</html>".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() -> fetcher().fetch(baseUrl + "/not-image"))
                .isInstanceOf(HtmlFetchException.class)
                .hasMessageContaining("이미지가 아닌");
    }

    @Test
    void 크기_상한을_넘는_이미지는_거부한다() {
        // Jsoup은 maxBodySize에서 조용히 잘라버리므로, 상한에 걸린 응답은 명시적으로 거부해야 한다.
        serveImage("/huge.png", new byte[MAX_IMAGE_BYTES * 4]);

        assertThatThrownBy(() -> fetcher().fetch(baseUrl + "/huge.png"))
                .isInstanceOf(HtmlFetchException.class)
                .hasMessageContaining("크기 상한");
    }

    @Test
    void 오류_상태코드는_거부한다() {
        server.createContext("/gone", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> fetcher().fetch(baseUrl + "/gone"))
                .isInstanceOf(HtmlFetchException.class)
                .hasMessageContaining("status=404");
    }

    @Test
    void 실제_가드는_내부_주소를_차단한다() {
        // 크롤링한 페이지가 준 og:image가 내부망을 가리키는 SSRF 시나리오.
        serveImage("/internal.png", new byte[] {1});
        PreviewImageFetcher guarded = new PreviewImageFetcher(
                new PrivateNetworkGuard(), 2000, MAX_IMAGE_BYTES, 5);

        assertThatThrownBy(() -> guarded.fetch(baseUrl + "/internal.png"))
                .isInstanceOf(HtmlFetchException.class);
    }
}
