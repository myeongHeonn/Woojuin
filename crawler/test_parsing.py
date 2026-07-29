"""parsing.py 순수 헬퍼 테스트. Scrapling(브라우저) 없이 실행된다.

실행: cd crawler && pytest
"""
from parsing import html_of, rejection_reason

AKAMAI_ACCESS_DENIED = (
    "<HTML><HEAD><TITLE>Access Denied</TITLE></HEAD><BODY>"
    "<H1>Access Denied</H1> ...errors.edgesuite.net...</BODY></HTML>"
)
AKAMAI_CHALLENGE = (
    '<html><body><div id="sec-if-cpt-container"></div>'
    "Powered and protected by Akamai</body></html>"
)
GOOD_PRODUCT = (
    '<html><head><meta property="og:title" content="상품명">'
    '<h1 class="prod-buy-header__title">쿠팡 상품</h1></head>'
    "<body>내용</body></html>"
)
# 구글이 스텔스 브라우저를 감지했을 때 보내는 캡차 페이지. share.google 링크로 실측했다.
GOOGLE_CAPTCHA = (
    '<html><head><title>https://www.google.com/search?q=x</title></head><body>'
    '<form action="/sorry/index?continue=https://www.google.com/search" id="captcha-form">'
    "</form></body></html>"
)
# reCAPTCHA를 정상적으로 쓰는 일반 사이트 — 차단으로 오인하면 안 된다.
LEGIT_RECAPTCHA_PAGE = (
    '<html><head><meta property="og:title" content="문의하기"></head><body>'
    '<script src="https://www.google.com/recaptcha/api.js"></script>'
    "<p>문의 내용을 남겨주세요</p></body></html>"
)


class TestRejectionReason:
    def test_normal_product_page_passes(self):
        assert rejection_reason(200, GOOD_PRODUCT) is None

    def test_empty_html_rejected(self):
        assert rejection_reason(200, "") == "empty html"

    def test_403_rejected(self):
        assert rejection_reason(403, "<html>x</html>") == "blocked: status=403"

    def test_akamai_access_denied_with_200_rejected(self):
        # 상태코드가 200이어도 본문이 접근 거부면 실패로 본다.
        assert rejection_reason(200, AKAMAI_ACCESS_DENIED) == "blocked: akamai access denied"

    def test_akamai_challenge_with_200_rejected(self):
        assert rejection_reason(200, AKAMAI_CHALLENGE) == "blocked: akamai challenge page"

    def test_detection_is_case_insensitive(self):
        upper = "<html>ACCESS DENIED errors.EDGESUITE.net</html>"
        assert rejection_reason(200, upper) == "blocked: akamai access denied"

    def test_429_rejected(self):
        # 레이트리밋을 통과시키면 Java가 차단 페이지를 정상 콘텐츠로 저장한다.
        assert rejection_reason(429, "<html>x</html>") == "blocked: status=429"

    def test_google_captcha_with_200_rejected(self):
        assert rejection_reason(200, GOOGLE_CAPTCHA) == "blocked: google automation captcha"

    def test_google_unusual_traffic_phrase_rejected(self):
        page = "<html><body>Our systems have detected unusual traffic</body></html>"
        assert rejection_reason(200, page) == "blocked: google automation captcha"

    def test_legit_recaptcha_page_passes(self):
        # 마커가 /sorry/index 로 좁혀져 있어 정상 사이트의 reCAPTCHA는 통과한다.
        assert rejection_reason(200, LEGIT_RECAPTCHA_PAGE) is None


class _FakePage:
    def __init__(self, body=None, html_content=None, encoding="utf-8"):
        if body is not None:
            self.body = body
        if html_content is not None:
            self.html_content = html_content
        self.encoding = encoding


class TestHtmlOf:
    def test_string_body(self):
        assert html_of(_FakePage(body="<html>ok</html>")) == "<html>ok</html>"

    def test_bytes_body_decoded(self):
        page = _FakePage(body="<html>한글</html>".encode("utf-8"))
        assert html_of(page) == "<html>한글</html>"

    def test_falls_back_to_html_content_when_body_empty(self):
        page = _FakePage(body="", html_content="<html>fallback</html>")
        assert html_of(page) == "<html>fallback</html>"

    def test_missing_everything_returns_empty(self):
        assert html_of(_FakePage()) == ""
