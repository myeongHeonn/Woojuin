"""Shazam 응답 해석 단위 테스트 — 네트워크 없이 돈다.

가장 중요한 검증은 **링크가 없으면 매치로 보지 않는다**는 것이다. 저장 방식이 "링크를
URL 아이템으로"이므로, 링크 없는 결과를 통과시키면 워치가 저장할 수 없는 것을 받아 든다.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import to_song  # noqa: E402


def track(**overrides):
    """라이브로 확인한 응답 모양(전영호 - Butter-Fly)을 축소한 픽스처."""
    data = {
        "title": "Butter-Fly",
        "subtitle": "전영호",
        "url": "https://www.shazam.com/track/620890971/butter-fly",
        "isrc": "KRA382300123",
        "images": {"coverart": "https://is1-ssl.mzstatic.com/image/cover.jpg"},
        "share": {
            "href": "https://www.shazam.com/track/620890971/butter-fly",
            "image": "https://is1-ssl.mzstatic.com/image/share.jpg",
        },
    }
    data.update(overrides)
    return {"matches": [{"id": "1"}], "track": data}


def test_찾은_곡의_제목_아티스트_링크_커버를_꺼낸다():
    song = to_song(track())

    assert song["found"] is True
    assert song["title"] == "Butter-Fly"
    assert song["artist"] == "전영호"
    assert song["link"] == "https://www.shazam.com/track/620890971/butter-fly"
    assert song["coverUrl"] == "https://is1-ssl.mzstatic.com/image/cover.jpg"
    assert song["isrc"] == "KRA382300123"


def test_매치가_없으면_찾지_못한_것이다():
    assert to_song({"matches": [], "tagid": "abc", "retryms": 5000}) == {"found": False}


def test_응답이_비어도_찾지_못한_것이다():
    assert to_song({}) == {"found": False}
    assert to_song(None) == {"found": False}


def test_url_이_없으면_share_href_로_떨어진다():
    data = track()
    del data["track"]["url"]

    assert to_song(data)["link"] == "https://www.shazam.com/track/620890971/butter-fly"


def test_링크가_아예_없으면_매치로_보지_않는다():
    """URL 아이템으로 저장할 수 없는 결과는 통과시키지 않는다."""
    data = track()
    del data["track"]["url"]
    data["track"]["share"] = {}

    assert to_song(data) == {"found": False}


def test_coverart_이_없으면_share_image_로_떨어진다():
    data = track()
    data["track"]["images"] = {}

    assert to_song(data)["coverUrl"] == "https://is1-ssl.mzstatic.com/image/share.jpg"


def test_커버가_아예_없어도_저장은_가능하다():
    data = track()
    data["track"]["images"] = {}
    data["track"]["share"] = {"href": "https://www.shazam.com/track/1/x"}

    song = to_song(data)

    assert song["found"] is True
    assert song["coverUrl"] is None
