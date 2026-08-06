# 노래 인식 사이드카 — shazamio(비공식 Shazam) 로 오디오 한 토막에서 곡을 알아낸다.
#
# ffmpeg 를 넣지 않는다. shazamio 는 pydub 으로 오디오를 읽는데 WAV 는 표준 라이브러리
# (wave)로 처리되어 외부 바이너리가 필요 없고, 워치가 보내는 것이 16kHz 모노 WAV 다.
# mp3 등을 받을 일이 생기면 그때 ffmpeg 를 더한다(이미지가 100MB 이상 커진다).
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py ./

EXPOSE 8003

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8003"]
