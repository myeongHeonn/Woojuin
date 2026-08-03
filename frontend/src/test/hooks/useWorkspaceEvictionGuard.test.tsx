import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useWorkspaceEvictionGuard } from '@/hooks/useWorkspaceEvictionGuard';
import { fetchMembers } from '@/services/workspaces';
import { fetchMyProfile } from '@/services/auth';
import type { WorkspaceMember } from '@/services/workspaces';
import type { UserProfile } from '@/services/auth';

vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  fetchMembers: vi.fn(),
}));
vi.mock('@/services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/auth')>()),
  fetchMyProfile: vi.fn(),
}));

const mockFetchMembers = vi.mocked(fetchMembers);
const mockFetchMyProfile = vi.mocked(fetchMyProfile);

const member = (userId: number): WorkspaceMember => ({
  userId,
  nickname: '멤버' + userId,
  email: `member${userId}@woojuin.com`,
  role: 'MEMBER',
  joinedAt: '',
  avatarColor: 'BLUE',
});

const profile = (id: number): UserProfile => ({
  id,
  email: 'me@woojuin.com',
  nickname: '나',
  profileImageUrl: null,
  provider: 'GOOGLE',
  emailVerified: true,
  avatarColor: 'WHITE',
  personalSpaceId: 1,
  personalTutorialCompleted: true,
  sharedWorkspaceTutorialCompleted: true,
});

/** 훅만 부르는 프로브 — 화면은 evicted 값만 보여준다 */
function Probe({ workspaceId }: { workspaceId: number }) {
  const { evicted } = useWorkspaceEvictionGuard(workspaceId);
  return <div data-testid="evicted">{String(evicted)}</div>;
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
  it('내가 멤버 목록에 있으면 추방 상태가 아니다', async () => {
    mockFetchMyProfile.mockResolvedValue(profile(1));
    mockFetchMembers.mockResolvedValue([member(1), member(2)]);

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
  });

  it('내가 멤버 목록에 없으면 추방 상태다', async () => {
    mockFetchMyProfile.mockResolvedValue(profile(1));
    mockFetchMembers.mockResolvedValue([member(2), member(3)]);

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('true');
  });

  it('멤버 목록 조회 자체가 실패하면 추방 상태로 보지 않는다', async () => {
    mockFetchMyProfile.mockResolvedValue(profile(1));
    mockFetchMembers.mockRejectedValue(new Error('403'));

    const { getByTestId } = await renderProbe();

    await expect.element(getByTestId('evicted')).toHaveTextContent('false');
  });
});
