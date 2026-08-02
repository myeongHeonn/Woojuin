#!/usr/bin/env python3
"""도커가 아직 들고 있는 과거 로그를 Loki 로 밀어 넣는다 (1회성 백필).

왜 필요한가: Promtail 은 **시작한 시점부터** 따라붙고 과거를 소급해 읽지 않는다(실측 확인).
그래서 Loki 를 붙이는 순간 이전의 로그는 Loki 에서 안 보인다. 다만 오래 사는 컨테이너
(postgres·redis·minio·jenkins)는 도커가 여전히 로그를 들고 있으므로, 그걸 원래 타임스탬프
그대로 밀어 넣으면 실시간 로그와 이어져 보인다.

배포마다 재생성되는 컨테이너(backend·aimix·crawler)의 **이전 세대 로그는 이미 삭제되어**
어떤 방법으로도 복구할 수 없다. 이 스크립트로 가져오는 것은 지금 살아 있는 컨테이너의 몫이다.

라벨은 Promtail 과 똑같이 붙인다(stack·service·container·stream). 그래야 백필한 로그와
실시간 로그가 같은 스트림으로 이어진다 — 라벨이 하나라도 다르면 Grafana 에서 별개로 보인다.

사용법 (서버에서, 모니터링 스택이 떠 있는 상태로):
    python3 backfill-docker-logs.py                    # 미리보기만 (아무것도 보내지 않는다)
    python3 backfill-docker-logs.py --push             # 실제 전송
    python3 backfill-docker-logs.py --push --days 6    # 6일치만 (기본 6)
    python3 backfill-docker-logs.py --push --filter jenkins

주의 1: Loki 설정의 reject_old_samples_max_age 가 168h(7일)라 그보다 오래된 줄은 서버가 거부한다.
        기본 --days 6 은 그 한계 안쪽으로 잡은 값이다.
주의 2: 푸시는 204 로 받아들여지지만 **바로 조회되지 않는다.** 쿼리어는 3시간보다 오래된 구간을
        인제스터에 묻지 않고 스토리지만 보는데, 방금 넣은 줄은 아직 메모리에 있기 때문이다.
        그래서 전송이 끝나면 이 스크립트가 POST /flush 로 강제 플러시한다(실측으로 확인한 동작).
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

LOKI_BASE_URL = 'http://127.0.0.1:3100'
LOKI_PUSH_URL = f'{LOKI_BASE_URL}/loki/api/v1/push'
LOKI_FLUSH_URL = f'{LOKI_BASE_URL}/flush'
BATCH_LINES = 500


def run(args: list[str]) -> str:
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8',
                          errors='replace').stdout


def containers(name_filter: str) -> list[str]:
    names = run(['docker', 'ps', '--format', '{{.Names}}']).split()
    return sorted(n for n in names if name_filter in n) if name_filter else sorted(names)


def compose_labels(container: str) -> dict[str, str]:
    """Promtail 이 쓰는 것과 같은 compose 라벨을 컨테이너에서 직접 읽는다."""
    fmt = ('{{index .Config.Labels "com.docker.compose.project"}}|'
           '{{index .Config.Labels "com.docker.compose.service"}}')
    project, _, service = run(['docker', 'inspect', '--format', fmt, container]).strip().partition('|')
    labels = {'container': container}
    # compose 로 띄우지 않은 컨테이너(jenkins 등)는 이 라벨이 없다 — Promtail 도 마찬가지라 맞다
    if project and project != '<no value>':
        labels['stack'] = project
    if service and service != '<no value>':
        labels['service'] = service
    return labels


def read_logs(container: str, since: str) -> dict[str, list[tuple[str, str]]]:
    """stdout·stderr 를 따로 읽는다 — Promtail 이 stream 라벨로 구분하므로 같이 맞춘다."""
    proc = subprocess.run(
        ['docker', 'logs', '--timestamps', '--since', since, container],
        capture_output=True, text=True, encoding='utf-8', errors='replace')
    out: dict[str, list[tuple[str, str]]] = {'stdout': [], 'stderr': []}
    for stream, blob in (('stdout', proc.stdout), ('stderr', proc.stderr)):
        for raw in blob.splitlines():
            ts, _, line = raw.partition(' ')
            if not _:
                continue
            try:
                # 도커는 RFC3339Nano 로 준다. 나노초 자리를 파이썬이 못 읽어 6자리로 자른다.
                head, _, frac = ts.partition('.')
                micros = (frac.rstrip('Z') + '000000')[:6]
                dt = datetime.strptime(head, '%Y-%m-%dT%H:%M:%S').replace(
                    tzinfo=timezone.utc, microsecond=int(micros))
            except ValueError:
                continue
            out[stream].append((str(int(dt.timestamp() * 1_000_000_000)), line))
    return out


def push(streams: list[dict], dry_run: bool) -> tuple[bool, str]:
    if dry_run:
        return True, 'dry-run'
    body = json.dumps({'streams': streams}).encode('utf-8')
    request = urllib.request.Request(LOKI_PUSH_URL, data=body,
                                     headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status < 300, f'HTTP {response.status}'
    except urllib.error.HTTPError as error:
        return False, f'HTTP {error.code}: {error.read().decode("utf-8", "replace")[:200]}'
    except OSError as error:
        return False, f'연결 실패: {error} (모니터링 스택이 떠 있고 3100 이 열려 있는지 확인)'


def main() -> int:
    parser = argparse.ArgumentParser(description='도커 과거 로그를 Loki 로 백필한다')
    parser.add_argument('--push', action='store_true', help='실제로 전송 (없으면 미리보기)')
    parser.add_argument('--days', type=int, default=6,
                        help='최근 N일치만 (기본 6 — Loki 의 7일 거부 한계 안쪽)')
    parser.add_argument('--filter', default='', help='컨테이너 이름에 포함된 문자열')
    args = parser.parse_args()

    since = (datetime.now(timezone.utc) - timedelta(days=args.days)).strftime('%Y-%m-%dT%H:%M:%SZ')
    targets = containers(args.filter)
    if not targets:
        print('대상 컨테이너가 없다')
        return 1

    print(f"{'[미리보기]' if not args.push else '[전송]'} {since} 이후 / 대상 {len(targets)}개\n")
    total, failed = 0, 0
    for container in targets:
        labels = compose_labels(container)
        by_stream = read_logs(container, since)
        count = sum(len(v) for v in by_stream.values())
        if not count:
            print(f'  {container:<34} 0줄 (건너뜀)')
            continue

        sent = 0
        for stream_name, values in by_stream.items():
            for i in range(0, len(values), BATCH_LINES):
                chunk = values[i:i + BATCH_LINES]
                ok, detail = push([{'stream': {**labels, 'stream': stream_name},
                                    'values': [list(v) for v in chunk]}], not args.push)
                if ok:
                    sent += len(chunk)
                else:
                    failed += 1
                    print(f'  {container:<34} 실패 — {detail}')
                    break
                time.sleep(0.05)  # 인제스트 레이트 제한(8MB/s)에 여유를 둔다
        oldest = min(int(v[0]) for vs in by_stream.values() for v in vs)
        print(f'  {container:<34} {sent}/{count}줄  '
              f'(가장 오래된 {datetime.fromtimestamp(oldest / 1e9).strftime("%m-%d %H:%M")})')
        total += sent

    print(f'\n{"보낼" if not args.push else "보낸"} 줄 수: {total}' + (f' / 실패 {failed}건' if failed else ''))
    if not args.push:
        print('실제로 넣으려면 --push 를 붙여 다시 실행')
        return 0
    # 플러시하지 않으면 방금 넣은 과거 로그가 조회되지 않는다 — 파일 상단 '주의 2' 참고
    try:
        urllib.request.urlopen(urllib.request.Request(LOKI_FLUSH_URL, data=b''), timeout=30)
        print('인제스터 플러시 완료 — Grafana 에서 바로 조회된다')
    except OSError as error:
        print(f'플러시 실패({error}) — 30분쯤 뒤 자동 플러시되면 보인다')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
