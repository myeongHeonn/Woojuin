import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { Provider, createStore } from 'jotai';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fetchEventSource, type FetchEventSourceInit } from '@microsoft/fetch-event-source';
import { useWorkspaceEvents } from '@/hooks/useWorkspaceEvents';
import { requestTokenRefresh } from '@/services/client';
import { accessTokenAtom } from '@/stores/authAtoms';

// 실제 통신은 막는다 — 검증 대상은 신호를 받았을 때의 무효화·재연결 판단이지 SSE 프로토콜이 아니다
vi.mock('@microsoft/fetch-event-source', () => ({ fetchEventSource: vi.fn() }));
vi.mock('@/services/client', () => ({ requestTokenRefresh: vi.fn() }));

const mockFetchEventSource = vi.mocked(fetchEventSource);
const mockRefresh = vi.mocked(requestTokenRefresh);

/** 훅만 부르는 프로브 — 화면은 필요 없다 */
function Probe({ workspaceId }: { workspaceId: number }) {
  useWorkspaceEvents(workspaceId);
  return null;
}

/** fetchEventSource에 넘어간 콜백(onopen/onmessage/...)을 꺼내 서버 역할을 흉내 낸다 */
const lastOptions = (): FetchEventSourceInit => {
  const call = mockFetchEventSource.mock.calls.at(-1);
  if (!call) throw new Error('fetchEventSource가 호출되지 않았다');
  return call[1]!;
};

const sseResponse = (status: number) =>
  new Response(null, {
    status,
    headers: status === 200 ? { 'content-type': 'text/event-stream' } : {},
  });

const message = (event: string) => ({ event, data: '', id: '', retry: undefined });

let queryClient: QueryClient;

const renderProbe = (workspaceId = 7, token: string | null = 'token') => {
  const store = createStore();
  store.set(accessTokenAtom, token);
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  vi.spyOn(queryClient, 'invalidateQueries');

  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <Probe workspaceId={workspaceId} />
      </QueryClientProvider>
    </Provider>,
  );
};

beforeEach(() => {
  vi.useFakeTimers();
  mockFetchEventSource.mockReset();
  mockFetchEventSource.mockReturnValue(new Promise(() => {})); // 연결이 열린 채 유지되는 상태
  mockRefresh.mockReset();
  mockRefresh.mockResolvedValue(null);
});

afterEach(() => {
  vi.useRealTimers();
  localStorage.clear();
});

describe('useWorkspaceEvents — 구독 조건', () => {
  it('토큰이 있으면 해당 워크스페이스 이벤트 주소로 연결한다', async () => {
    await renderProbe(7);

    expect(mockFetchEventSource).toHaveBeenCalledTimes(1);
    expect(mockFetchEventSource.mock.calls[0][0]).toMatch(/\/workspaces\/7\/events$/);
  });

  it('토큰이 없으면 연결하지 않는다', async () => {
    await renderProbe(7, null);

    expect(mockFetchEventSource).not.toHaveBeenCalled();
  });
});

describe('useWorkspaceEvents — 무효화 병합(디바운스)', () => {
  it('같은 종류 신호가 몰려와도 무효화는 한 번만 나간다', async () => {
    await renderProbe();
    const { onmessage } = lastOptions();

    // 아이템 3개가 잇달아 처리 완료된 상황
    onmessage!(message('item'));
    onmessage!(message('item'));
    onmessage!(message('item'));
    expect(queryClient.invalidateQueries).not.toHaveBeenCalled(); // 아직 병합 창 안

    vi.advanceTimersByTime(500);

    // item 은 5개 캐시를 무효화한다 — 신호 3번이 왔어도 5번이면 한 번만 돈 것이다
    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(5);
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['items'] });
  });

  it('다른 종류 신호는 각자 무효화된다', async () => {
    await renderProbe();
    const { onmessage } = lastOptions();

    onmessage!(message('category')); // 2개 캐시
    onmessage!(message('member')); // 2개 캐시
    vi.advanceTimersByTime(500);

    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(4);
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['categories'] });
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['members'] });
  });

  it('연결 확인용(connected) 신호는 무효화를 일으키지 않는다', async () => {
    await renderProbe();
    const { onmessage } = lastOptions();

    onmessage!(message('connected'));
    vi.advanceTimersByTime(500);

    expect(queryClient.invalidateQueries).not.toHaveBeenCalled();
  });

  it('언마운트 때 모아둔 무효화를 버리지 않고 반영한다', async () => {
    const screen = await renderProbe();
    const { onmessage } = lastOptions();

    onmessage!(message('item'));
    screen.unmount(); // 병합 창(400ms)이 지나기 전에 워크스페이스를 떠난다

    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['items'] });
  });
});

describe('useWorkspaceEvents — 연결 실패 처리', () => {
  it('401이면 토큰 갱신을 걸고 이 연결은 접는다', async () => {
    await renderProbe();
    const { onopen } = lastOptions();

    await expect(onopen!(sseResponse(401))).rejects.toThrow('401');
    expect(mockRefresh).toHaveBeenCalledTimes(1);
  });

  it('403은 갱신 없이 연결만 접는다(재시도해도 결과가 같다)', async () => {
    await renderProbe();
    const { onopen } = lastOptions();

    await expect(onopen!(sseResponse(403))).rejects.toThrow('403');
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it('5xx는 일시 장애로 보고 재시도 대상으로 남긴다', async () => {
    await renderProbe();
    const { onopen, onerror } = lastOptions();

    const error = await onopen!(sseResponse(502)).catch((e) => e);
    // 5xx 오류는 onerror가 재시도 간격(ms)을 돌려줘 재연결을 잇는다
    expect(onerror!(error)).toBeTypeOf('number');
  });
});
