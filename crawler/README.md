# Woojuin Crawler (Scrapling 사이드카)

Java 백엔드의 Jsoup 기반 `HtmlFetcher`가 **JS 렌더링 SPA·봇 차단(Cloudflare·Akamai 등)**
페이지에서 빈 껍데기만 받아올 때, 폴백으로 호출되는 크롤링 서비스다.
[Scrapling](https://github.com/D4Vinci/Scrapling)의 `StealthyFetcher`(camoufox
스텔스 브라우저)로 실제 브라우저처럼 렌더한 최종 HTML을 돌려준다.

## 봇 차단 우회 전략

쿠팡처럼 **Akamai Bot Manager**를 쓰는 사이트는 `solve_cloudflare`(Cloudflare
Turnstile 전용)로는 뚫리지 않는다. 대신 다음 조합을 쓴다:

- **`google_search=True`** (기본값) — 구글 검색 유입 흐름을 흉내내 referrer에
  민감한 진입 차단을 낮춘다. 쿠팡 Akamai 우회의 실질적인 핵심.
- **network_idle 폴백** — `network_idle`을 명시하지 않으면 빠른 모드(`False`)로
  먼저 시도하고, 차단·challenge·빈 HTML이면 느린 모드(`True`)로 한 번 더 렌더한다.
- **challenge 감지** — Akamai는 HTTP 200으로도 도전 페이지(`sec-if-cpt-container`,
  `Access Denied`/`errors.edgesuite.net` 등)를 돌려준다. 이런 페이지는 상품
  콘텐츠가 없으므로 성공으로 보지 않고 `502`로 처리해 다음 시도/Jsoup 폴백에 맡긴다.

## 흐름

```
UrlItemProcessor / UrlBatchExportRunner
        │  HtmlFetcher.fetch(url)
        ▼
FallbackHtmlFetcher (@Primary)
        ├─ 1) JsoupHtmlFetcher      (빠른 경로: 정적 HTML)
        └─ 2) ScraplingHtmlFetcher  (폴백: Jsoup이 실패하거나 결과가 빈약할 때만)
                    │  POST /render {url}
                    ▼
              이 서비스 (StealthyFetcher) ──▶ {url, status, html}
```

Java는 받은 `html`을 `Jsoup.parse(html, url)`로 감싸 기존 OG 스크래퍼·본문
추출을 그대로 태운다. 브라우저는 느리고 무거워서 **폴백 경로에서만** 호출된다.

## API

- `GET /health` → `{"status":"ok"}`
- `POST /render` — body `{ "url": "...", "solve_cloudflare": true?, "google_search": true?, "network_idle": true? }`
  → `{ "url": "<최종 URL>", "status": 200, "html": "<렌더된 HTML>" }`
  - 세 옵션 모두 생략 가능. 생략 시 env 기본값을 쓰고, `network_idle`은 생략 시
    빠른 모드→느린 모드로 자동 폴백한다.
  - 실패 시 `502` (Java는 폴백 실패로 처리하고 Jsoup 결과로 되돌아간다).
    차단·challenge·빈 HTML도 `502`로 취급한다.

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `CRAWLER_HEADLESS` | `true` | 헤드리스 브라우저 |
| `CRAWLER_NETWORK_IDLE` | `true` | 요청이 `network_idle`을 명시했을 때의 기본 대기값 |
| `CRAWLER_SOLVE_CLOUDFLARE` | `true` | Cloudflare Turnstile 우회 시도(느려짐; Akamai엔 무효) |
| `CRAWLER_GOOGLE_SEARCH` | `true` | 구글 검색 유입 흐름으로 진입(Akamai 우회 핵심) |
| `CRAWLER_TIMEOUT_MS` | `30000` | 렌더 타임아웃(ms) |
| `CRAWLER_MAX_CONCURRENCY` | `2` | 동시 브라우저 수 상한 |

## 로컬 실행

```bash
# 도커 (권장 — 브라우저 의존성 포함)
docker compose up -d crawler

# 또는 직접
cd crawler
pip install -r requirements.txt
scrapling install          # 브라우저 다운로드 (최초 1회)
uvicorn main:app --host 0.0.0.0 --port 8001
```

## Java 쪽 연동 켜기

백엔드는 기본적으로 크롤러 폴백이 **꺼져 있다**(`CRAWLER_ENABLED=false`).
이 서비스를 띄운 뒤 백엔드 환경변수에서 켠다:

```
CRAWLER_ENABLED=true
CRAWLER_BASE_URL=http://localhost:8001
```
