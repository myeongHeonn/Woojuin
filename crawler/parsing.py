"""렌더 결과를 판정·추출하는 순수 헬퍼.

무거운 Scrapling(브라우저) 의존성 없이 단독으로 import·테스트할 수 있도록
main.py의 라우팅 로직과 분리해 둔다.
"""


def rejection_reason(status: int, html: str) -> str | None:
    """렌더 결과가 못 쓸 페이지면 이유 문자열을, 정상이면 None을 반환한다.

    HTTP 200이라도 Akamai가 도전 페이지를 돌려줄 수 있어(sec-if-cpt-container 등),
    상태코드만으로 성공을 판단하지 않는다. 마커는 쿠팡 Akamai 기준이지만 일반
    사이트의 정상 페이지에는 등장하지 않으므로 오탐 위험이 낮다.
    """
    if not html:
        return "empty html"
    # 403은 차단, 429는 레이트리밋. 429를 통과시키면 Java가 "성공"으로 받아 차단 페이지를
    # 파싱하고, FallbackHtmlFetcher의 "크롤러 실패 시 Jsoup 결과 사용" 안전망도 안 돈다.
    if status in (403, 429):
        return f"blocked: status={status}"

    lowered = html.lower()
    # 구글 자동화 차단(캡차) 페이지. share.google 링크를 렌더할 때 실측으로 확인했다 —
    # google.com/sorry/index 로 보내면서 상태코드는 200이나 429로 온다. 마커를 구글
    # 고유 경로로 좁혀서, reCAPTCHA를 정상적으로 쓰는 일반 사이트가 걸리지 않게 한다.
    if "/sorry/index" in lowered or "unusual traffic" in lowered:
        return "blocked: google automation captcha"
    # 아카마이 접근 거부 페이지 (200으로 올 수도 있어 상태코드와 별개로 본다)
    if "errors.edgesuite.net" in lowered or "access denied" in lowered:
        return "blocked: akamai access denied"
    # 아카마이 봇 매니저 challenge/센서 페이지
    if "sec-if-cpt-container" in lowered or "powered and protected by akamai" in lowered:
        return "blocked: akamai challenge page"
    return None


def html_of(page) -> str:
    """Response에서 전체 HTML 문자열을 최대한 견고하게 뽑는다.

    브라우저 페처의 body는 보통 렌더된 DOM 문자열이지만 버전에 따라 bytes일 수
    있어 둘 다 처리하고, 비어 있으면 html_content로 한 번 더 시도한다.
    """
    raw = getattr(page, "body", None)
    if isinstance(raw, (bytes, bytearray)):
        encoding = getattr(page, "encoding", "utf-8") or "utf-8"
        html = raw.decode(encoding, errors="replace")
    else:
        html = raw or ""
    if not html:
        html = getattr(page, "html_content", "") or ""
    return html
