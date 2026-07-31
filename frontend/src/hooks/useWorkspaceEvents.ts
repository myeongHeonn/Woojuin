import { useEffect } from 'react';
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { accessTokenAtom } from '@/stores/authAtoms';
import { requestTokenRefresh } from '@/services/client';

/** 서버가 보내는 변경 신호 종류 — 백엔드 WorkspaceEventType 과 같은 값을 쓴다 */
type WorkspaceEventType = 'item' | 'category' | 'workspace' | 'member';

const EVENT_STREAM = 'text/event-stream';
/** 일시적 실패(네트워크 끊김 등) 재연결 간격 */
const RETRY_DELAY_MS = 3000;
/**
 * 무효화 병합 간격. URL 배치·원클릭 저장처럼 아이템 N개가 잇달아 완료되면 신호도 N번
 * 오는데, 순차 도착이라 react-query가 합쳐주지 않아 그대로 N번 재조회가 나간다.
 * 이 간격 안에 온 같은 종류 신호를 한 번의 무효화로 합친다.
 */
const INVALIDATE_DEBOUNCE_MS = 400;

/** 재시도해도 결과가 같은 실패(4xx) — 이걸 던지면 재연결을 멈춘다 */
class FatalConnectionError extends Error {
  constructor(status: number) {
    super(`SSE 연결이 거부됨: ${status}`);
    this.name = 'FatalConnectionError';
  }
}

/**
 * 신호 종류별로 무효화할 캐시.
 *
 * 서버는 "무엇이 바뀌었다"만 알리고 데이터는 안 보낸다 — 실제 조회는 늘 쓰던 쿼리가
 * 다시 하도록 두는 편이 서버가 클라이언트 캐시 구조를 몰라도 되어 단순하다.
 * 백엔드가 아직 안 보내는 종류도 미리 매핑해 둔다(보내기 시작하면 프론트는 그대로 동작).
 */
const INVALIDATION_TARGETS: Record<WorkspaceEventType, string[][]> = {
  // 아이템 생성·처리완료·수정·삭제·즐겨찾기·휴지통 — 목록/상세/지도/성좌가 모두 달라진다
  item: [['items'], ['item'], ['universe'], ['map-places'], ['trash']],
  category: [['categories'], ['universe']],
  workspace: [['workspaces']],
  member: [['members'], ['workspaces']],
};

const invalidate = (queryClient: QueryClient, type: WorkspaceEventType) => {
  INVALIDATION_TARGETS[type]?.forEach((queryKey) => {
    queryClient.invalidateQueries({ queryKey });
  });
};

/**
 * 워크스페이스 변경 신호(SSE) 구독 — 폴링을 대신해 남의 변경·처리 완료를 즉시 반영한다.
 *
 * 표준 EventSource 는 Authorization 헤더를 못 실어 토큰을 쿼리스트링에 넣어야 하는데,
 * 그러면 서버 액세스 로그에 토큰이 남는다. fetch 기반 클라이언트를 써서 다른 API 와
 * 똑같이 헤더로 인증한다.
 *
 * accessToken 을 의존성에 두는 이유: 토큰이 갱신되면(refresh) 낡은 토큰으로 열린 연결을
 * 버리고 새 토큰으로 다시 붙어야 한다.
 */
export function useWorkspaceEvents(workspaceId: number) {
  const queryClient = useQueryClient();
  const accessToken = useAtomValue(accessTokenAtom);

  useEffect(() => {
    if (!Number.isFinite(workspaceId) || !accessToken) return;

    // 언마운트·워크스페이스 전환 시 연결을 끊는다. 안 끊으면 워크스페이스를 옮길 때마다
    // 연결이 쌓여 이전 워크스페이스의 신호까지 계속 받는다.
    const controller = new AbortController();
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

    // 같은 종류 신호가 몰려오면 무효화를 한 번으로 합친다(INVALIDATE_DEBOUNCE_MS 참고)
    const pendingTypes = new Set<WorkspaceEventType>();
    let flushTimer: ReturnType<typeof setTimeout> | undefined;
    const flush = () => {
      flushTimer = undefined;
      pendingTypes.forEach((type) => invalidate(queryClient, type));
      pendingTypes.clear();
    };
    const scheduleInvalidate = (type: WorkspaceEventType) => {
      pendingTypes.add(type);
      flushTimer ??= setTimeout(flush, INVALIDATE_DEBOUNCE_MS);
    };

    fetchEventSource(`${baseUrl}/workspaces/${workspaceId}/events`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal: controller.signal,
      // 탭이 숨으면 연결을 닫아 자원을 아낀다. 다시 보일 때 재연결되고, 그 사이 놓친
      // 변경은 refetchOnWindowFocus(main.tsx)가 메꾼다.
      openWhenHidden: false,

      async onopen(response) {
        const contentType = response.headers.get('content-type');
        if (response.ok && contentType?.includes(EVENT_STREAM)) return;

        // 401은 토큰 만료 — SSE는 axios 인터셉터를 안 타므로 여기서 직접 갱신을 걸어야
        // 한다. 갱신이 성공하면 accessToken 아톰이 바뀌어 이 effect가 다시 돌고 새 토큰으로
        // 재연결된다(실패하면 토큰이 비워져 연결을 더 안 연다). 기다릴 필요는 없어서 안 기다린다.
        if (response.status === 401) {
          void requestTokenRefresh();
          throw new FatalConnectionError(response.status);
        }
        // 그 외 4xx 는 다시 시도해도 결과가 같다(권한 없음·엔드포인트 없음).
        // 재시도하면 요청만 폭주하므로 연결을 접는다.
        if (response.status >= 400 && response.status < 500) {
          throw new FatalConnectionError(response.status);
        }
        // 5xx·프록시 오류 등은 일시적일 수 있어 재시도에 맡긴다
        throw new Error(`SSE 연결 실패: ${response.status}`);
      },

      onmessage(event) {
        // 연결 확인용(connected)은 무시하고 실제 변경 신호만 처리한다
        if (event.event in INVALIDATION_TARGETS) {
          scheduleInvalidate(event.event as WorkspaceEventType);
        }
      },

      onclose() {
        // fetch-event-source는 서버가 정상적으로 스트림을 닫으면 기본적으로 재시도하지 않는다.
        // 서버의 30분 timeout 뒤에도 계속 구독하려면 오류 경로로 넘겨 onerror의 재연결 정책을 탄다.
        throw new Error('SSE 스트림이 종료됨');
      },

      onerror(err) {
        // 던지면 재연결을 포기한다 — 되풀이해도 소용없는 4xx 만 그렇게 한다.
        if (err instanceof FatalConnectionError) throw err;
        // 나머지(네트워크 끊김 등)는 간격을 두고 다시 시도한다(반환값 ms)
        return RETRY_DELAY_MS;
      },
    }).catch(() => {
      // abort 로 끊었거나 위에서 포기한 경우 — 둘 다 의도한 종료라 조용히 넘어간다
    });

    return () => {
      controller.abort();
      // 모아둔 무효화가 있으면 버리지 않고 즉시 반영한다 — 버리면 방금 바뀐 데이터가
      // 신선한 캐시로 남는다
      if (flushTimer !== undefined) {
        clearTimeout(flushTimer);
        flush();
      }
    };
  }, [workspaceId, accessToken, queryClient]);
}
