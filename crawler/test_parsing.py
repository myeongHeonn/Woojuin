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
