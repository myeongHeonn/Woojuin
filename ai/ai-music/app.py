"""노래 인식 사이드카 — 오디오 한 토막을 받아 곡을 알아낸다 (FR-055).

워치가 손목에서 12초를 녹음해 백엔드로 올리고, 백엔드가 이 서비스를 부른다. 응답의
링크를 워치가 URL 아이템으로 저장하면 기존 크롤·AI 파이프라인이 제목·썸네일을 채운다 —
장소 저장이 카카오맵 링크를 저장하는 것과 같은 방식이라 서버 스키마 변경이 없다.

**왜 사이드카인가.** 쓸 만한 무료 경로가 파이썬 라이브러리(shazamio)뿐이고, 이 레포는
이미 파이썬 사이드카 둘(ai-mix, crawler)을 같은 방식으로 운영한다. 자바에서 흉내 내는
대신 그 패턴을 한 번 더 쓴다.

**한계를 알고 쓴다.** shazamio 는 Shazam 내부 API 를 리버스 엔지니어링한 비공식
라이브러리다. 키도 과금도 없는 대신 (1) 이용약관 위반 소지가 있어 상용 출시에는 쓸 수
없고, (2) 예고 없이 깨진다 — 실제로 같은 라이브러리의 `search_track` 은 확인 시점에 이미
깨져 있었다(`recognize` 는 정상). 그래서 백엔드가 이 서비스 실패를 폴백(AudD)으로 받게
설계했고, 이 서비스는 **실패를 숨기지 않고 그대로 올린다**.
"""

from __future__ import annotations

import asyncio
import logging
import os
import tempfile
from typing import Any

from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from shazamio import Shazam

logger = logging.getLogger("ai-music")

app = FastAPI(
    title="Woojuin AI Music",
    version="1.0.0",
    description="우주인 노래 인식 사이드카 (shazamio)",
)

# 오디오 상한. 워치가 보내는 12초 16kHz 모노 WAV 가 약 375KB 라 넉넉하다. 이보다 크면
# 워치가 아닌 곳에서 온 요청이거나 형식이 잘못된 것이니 인식 시도 전에 자른다.
MAX_AUDIO_BYTES = 4 * 1024 * 1024

# 한 번의 인식에 허용하는 시간. Shazam 왕복은 보통 1~3초인데, 응답이 없을 때 백엔드의
# 요청을 무한정 붙잡으면 워치 화면이 "알아듣고 있어요"에서 멈춘다.
RECOGNIZE_TIMEOUT_SECONDS = 20.0


def success(data: Any) -> dict[str, Any]:
    return {"status": 200, "message": "success", "data": data}


@app.get("/health")
def health() -> dict[str, Any]:
    return success({"status": "ok"})


@app.post("/recognize")
async def recognize(file: UploadFile = File(...)) -> Any:
    """오디오 한 토막 → 곡 정보.

    성공 응답의 ``found`` 가 거짓이면 **곡을 찾지 못한 것**이고(주변이 조용했거나 카탈로그에
    없는 곡), 그건 오류가 아니다. 호출부가 "찾지 못했어요"와 "인식이 실패했어요"를 구분해
    보여줄 수 있어야 하므로 둘을 다른 상태로 답한다.
    """
    audio = await file.read()
    if not audio:
        return JSONResponse(status_code=400, content={"status": 400, "message": "오디오가 비어 있습니다", "data": None})
    if len(audio) > MAX_AUDIO_BYTES:
        return JSONResponse(
            status_code=413,
            content={"status": 413, "message": f"오디오가 너무 큽니다({len(audio)} bytes)", "data": None},
        )

    # shazamio 는 경로를 받는다(내부에서 읽어 지문을 만든다). 요청이 끝나면 지운다 —
    # 남길 이유가 없고, 사용자가 있던 공간의 소리라 더더욱 남기지 않는다.
    suffix = os.path.splitext(file.filename or "clip.wav")[1] or ".wav"
    path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as handle:
            handle.write(audio)
            path = handle.name
        try:
            result = await asyncio.wait_for(Shazam().recognize(path), RECOGNIZE_TIMEOUT_SECONDS)
        except asyncio.TimeoutError:
            logger.warning("인식 시간 초과 (%.0fs)", RECOGNIZE_TIMEOUT_SECONDS)
            return JSONResponse(
                status_code=504,
                content={"status": 504, "message": "인식이 시간 안에 끝나지 않았습니다", "data": None},
            )
        except Exception as error:  # noqa: BLE001 — 비공식 API 는 어떤 식으로든 깨질 수 있다
            logger.warning("인식 실패: %s: %s", type(error).__name__, error)
            return JSONResponse(
                status_code=502,
                content={"status": 502, "message": f"인식에 실패했습니다({type(error).__name__})", "data": None},
            )
    finally:
        if path:
            try:
                os.unlink(path)
            except OSError:
                pass

    return success(to_song(result))


def to_song(result: dict[str, Any]) -> dict[str, Any]:
    """Shazam 응답에서 우리가 쓰는 것만 꺼낸다.

    라이브로 확인한 모양(전영호 - Butter-Fly): ``track.title``·``track.subtitle``(아티스트),
    ``track.url``·``track.share.href``(Shazam 트랙 페이지), ``track.images.coverart``.
    **``hub.providers`` 는 null 이었다** — 스포티파이·애플 링크는 오지 않으므로 저장할
    링크는 Shazam 트랙 페이지다.
    """
    if not result or not result.get("matches"):
        return {"found": False}

    track = result.get("track") or {}
    share = track.get("share") or {}
    images = track.get("images") or {}
    link = track.get("url") or share.get("href")
    if not link:
        # 링크가 없으면 URL 아이템으로 저장할 수 없다 — 찾은 것으로 치지 않는다.
        # (제목만 저장하는 폴백은 두지 않는다: 썸네일도 링크도 없는 아이템이 남는다)
        logger.warning("링크 없는 인식 결과 — 매치로 보지 않는다: %s", track.get("title"))
        return {"found": False}

    return {
        "found": True,
        "title": track.get("title"),
        "artist": track.get("subtitle"),
        "link": link,
        "coverUrl": images.get("coverart") or share.get("image"),
        "isrc": track.get("isrc"),
    }
