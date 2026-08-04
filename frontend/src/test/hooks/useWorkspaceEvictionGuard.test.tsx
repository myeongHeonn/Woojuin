import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
import { useWorkspaceEvictionGuard } from '@/hooks/useWorkspaceEvictionGuard';
import { fetchMembers } from '@/services/workspaces';
import type { WorkspaceMember } from '@/services/workspaces';

vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  fetchMembers: vi.fn(),
}));

const mockFetchMembers = vi.mocked(fetchMembers);

const member = (userId: number): WorkspaceMember => ({
  userId,
  nickname: '멤버' + userId,
  email: `member${userId}@woojuin.com`,
  role: 'MEMBER',
  joinedAt: '',
  avatarColor: 'BLUE',
});

/** axios가 실제로 만드는 것과 같은 모양의 에러 — response.status로 분기하므로 이 형태가 맞아야 한다 */
const axiosErrorWithStatus = (status: number) =>
  new AxiosError('요청 실패', String(status), undefined, undefined, {
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data: null,
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

const renderProbe = async (workspaceId = 10) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = await render(
    <QueryClientProvider client={queryClient}>
      <Probe workspaceId={workspaceId} />
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
};

describe('useWorkspaceEvictionGuard', () => {
  it('멤버 목록 조회가 성공하면 추방도 권한 없음도 아니다', async () => {
    mockFetchMembers.mockResolvedValue([member(1), member(2)]);

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
    await expect.element(getByTestId('access-denied')).toHaveTextContent('false');
  });

  it('첫 조회부터 403이면 접근 권한이 없는 것이다 (초대 없이 남의 워크스페이스 URL로 바로 들어온 경우 — 추방 아님)', async () => {
    mockFetchMembers.mockRejectedValue(axiosErrorWithStatus(403));

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('access-denied')).toHaveTextContent('true');
    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
  });

  it('한 번 성공적으로 조회된 뒤 403이 나면 추방 상태다 (더 이상 멤버가 아님)', async () => {
    mockFetchMembers.mockResolvedValueOnce([member(1)]);
    const { getByTestId, queryClient } = await renderProbe();
    await expect.element(getByTestId('evicted')).toHaveTextContent('false');

    mockFetchMembers.mockRejectedValueOnce(axiosErrorWithStatus(403));
    await queryClient.refetchQueries({ queryKey: ['members', 10] });

    await expect.element(getByTestId('evicted')).toHaveTextContent('true');
    await expect.element(getByTestId('access-denied')).toHaveTextContent('false');
  });

  it('403이 아닌 다른 실패(네트워크 오류 등)는 둘 다로 보지 않는다', async () => {
    mockFetchMembers.mockRejectedValue(axiosErrorWithStatus(500));

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
    await expect.element(getByTestId('access-denied')).toHaveTextContent('false');
  });
});
