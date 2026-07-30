"""
Scrapling 기반 크롤링 사이드카.

Java 백엔드의 Jsoup HtmlFetcher는 단순 HTTP GET이라, JS로 렌더링되는 SPA나
Cloudflare·Akamai 같은 봇 차단이 걸린 사이트에서는 빈 껍데기/차단 페이지만 받아온다.
그럴 때 폴백으로 이 서비스를 호출한다 — StealthyFetcher(camoufox 스텔스 브라우저)로
실제 브라우저처럼 렌더한 최종 HTML을 그대로 돌려주면, Java 쪽은 그 HTML을
Jsoup.parse로 감싸 기존 OG 스크래퍼/본문 추출 파이프라인을 그대로 태운다.

봇 차단 우회에 특히 효과적인 조합(쿠팡 Akamai에서 확인):
  - google_search=True — 구글 검색을 거쳐 들어가는 유입 흐름을 흉내내, referrer에
    민감한 사이트(쿠팡 등)의 진입 차단을 낮춘다. solve_cloudflare는 Cloudflare
    Turnstile 전용이라 Akamai엔 도움이 안 되므로, 이쪽이 실질적인 우회 수단이다.
  - network_idle 폴백 — 먼저 빠른 모드(network_idle=False)로 시도하고, 결과가
    나쁘면(차단·challenge·빈 HTML) 느린 모드(network_idle=True)로 한 번 더 렌더한다.
  - challenge 감지 — Akamai는 HTTP 200으로도 도전 페이지(sec-if-cpt-container 등)를
    돌려줄 수 있다. 이걸 그대로 성공 처리하면 Java가 쓰레기 HTML을 파싱하므로,
    상품 콘텐츠가 없는 도전/차단 페이지는 실패로 취급해 다음 시도로 넘긴다.

브라우저는 무겁고 느려서(요청당 수 초) 폴백 경로에서만 호출된다. 동시에 뜨는
브라우저 수는 세마포어로 묶어 OOM을 막는다.
"""
import os
import threading

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from scrapling.fetchers import StealthyFetcher

from parsing import html_of, rejection_reason

HEADLESS = os.getenv("CRAWLER_HEADLESS", "true").lower() != "false"
NETWORK_IDLE = os.getenv("CRAWLER_NETWORK_IDLE", "true").lower() != "false"
SOLVE_CLOUDFLARE = os.getenv("CRAWLER_SOLVE_CLOUDFLARE", "true").lower() != "false"
GOOGLE_SEARCH = os.getenv("CRAWLER_GOOGLE_SEARCH", "true").lower() != "false"
TIMEOUT_MS = int(os.getenv("CRAWLER_TIMEOUT_MS", "30000"))
MAX_CONCURRENCY = int(os.getenv("CRAWLER_MAX_CONCURRENCY", "2"))

# 배치 도구가 8병렬로 때려도 한 번에 뜨는 브라우저 수를 이만큼으로 묶어 메모리를 지킨다.
_slots = threading.BoundedSemaphore(MAX_CONCURRENCY)

app = FastAPI(title="Woojuin Crawler", version="0.2.0")


class RenderRequest(BaseModel):
    url: str
    # None이면 서버 기본값(env)을 쓴다. 호출부가 페이지별로 덮어쓸 수 있게 열어둔다.
    solve_cloudflare: bool | None = None
    google_search: bool | None = None
    # None이면 fast(False)→idle(True) 2단계 폴백. 값을 명시하면 그 모드로 1회만 시도한다.
    network_idle: bool | None = None


class RenderResponse(BaseModel):
    url: str      # 리다이렉트까지 따라간 최종 URL (Jsoup baseUri로 쓰인다)
    status: int
    html: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/render", response_model=RenderResponse)
def render(req: RenderRequest):
    solve = SOLVE_CLOUDFLARE if req.solve_cloudflare is None else req.solve_cloudflare
    google = GOOGLE_SEARCH if req.google_search is None else req.google_search

    # network_idle을 명시하면 그 모드로 1회. 안 하면 빠른 모드 → 느린 모드로 폴백한다.
    # (느린 모드가 challenge/JS 렌더를 더 잘 통과하지만 그만큼 느려서 뒤로 미룬다.)
    attempts = [req.network_idle] if req.network_idle is not None else [False, True]

    last_error = "render failed"
    # 엔드포인트를 async가 아닌 def로 두면 FastAPI가 스레드풀에서 실행한다 —
    # Scrapling.fetch는 블로킹이라 이벤트 루프를 막지 않게 이 편이 안전하다.
    with _slots:
        for idle in attempts:
            try:
                page = StealthyFetcher.fetch(
                    req.url,
                    headless=HEADLESS,
                    google_search=google,
                    solve_cloudflare=solve,
                    network_idle=idle,
                    timeout=TIMEOUT_MS,
                )
            except Exception as exc:  # noqa: BLE001 - 어떤 실패든 다음 시도/폴백으로 넘긴다
                last_error = f"render failed: {exc}"
                continue

            html = html_of(page)
            status = getattr(page, "status", 0) or 0
            reason = rejection_reason(status, html)
            if reason is None:
                return RenderResponse(
                    url=getattr(page, "url", None) or req.url,
                    status=status,
                    html=html,
                )
            last_error = reason

    # 모든 시도가 차단/challenge/빈 HTML로 끝났다. 502로 변환해 Java가 폴백 실패로
    # 처리하도록 한다(Jsoup이 받아둔 결과가 있으면 그걸 쓰고, 없으면 예외).
    raise HTTPException(status_code=502, detail=last_error)
