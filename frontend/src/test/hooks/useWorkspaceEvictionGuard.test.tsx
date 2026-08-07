import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import { useWorkspaceEvictionGuard } from '@/hooks/useWorkspaceEvictionGuard';
import { fetchMembers } from '@/services/workspaces';

vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  fetchMembers: vi.fn(),
}));

const mockFetchMembers = vi.mocked(fetchMembers);

/**
 * axios가 실제로 만드는 것과 같은 모양의 에러 — response.status/response.data.message로
 * 분기하므로 이 형태가 맞아야 한다. message는 백엔드 ApiResponse의 message 필드다.
 */
const axiosErrorWithStatus = (status: number, message = '') =>
  new AxiosError('요청 실패', String(status), undefined, undefined, {
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data: { status, message, data: null },
  });

/** 훅만 부르는 프로브 — 화면은 evicted/accessDenied 값만 보여준다 */
function Probe({ workspaceId }: { workspaceId: number }) {
  const { evicted, accessDenied } = useWorkspaceEvictionGuard(workspaceId);
  return (
    <div>
      <div data-testid="evicted">{String(evicted)}</div>
      <div data-testid="access-denied">{String(accessDenied)}</div>
    </div>
  );
}

const renderProbe = (workspaceId = 10) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <Probe workspaceId={workspaceId} />
    </QueryClientProvider>,
  );
};

describe('useWorkspaceEvictionGuard', () => {
  it('멤버 목록 조회가 성공하면 추방도 권한 없음도 아니다', async () => {
    mockFetchMembers.mockResolvedValue([]);

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
    await expect.element(getByTestId('access-denied')).toHaveTextContent('false');
  });

  it('추방 이력이 있는 403(WorkspaceBannedException)이면 추방 상태다 — 이미 보고 있던 화면에서 실시간으로도, 모른 채 나중에 들어와도 동일하다', async () => {
    mockFetchMembers.mockRejectedValue(
      axiosErrorWithStatus(
        403,
        'COMMON_403: 이 워크스페이스에서 추방된 이력이 있어 재입장할 수 없습니다',
      ),
    );

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('true');
    await expect.element(getByTestId('access-denied')).toHaveTextContent('false');
  });

  it('추방 이력이 없는 403(자진 탈퇴했거나 애초에 멤버였던 적 없음)이면 접근 권한 없음이다', async () => {
    mockFetchMembers.mockRejectedValue(
      axiosErrorWithStatus(403, 'COMMON_403: 워크스페이스 멤버가 아닙니다'),
    );

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('access-denied')).toHaveTextContent('true');
    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
  });

  it('403이 아닌 다른 실패(네트워크 오류 등)는 둘 다로 보지 않는다', async () => {
    mockFetchMembers.mockRejectedValue(axiosErrorWithStatus(500));

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
    await expect.element(getByTestId('access-denied')).toHaveTextContent('false');
  });
});
