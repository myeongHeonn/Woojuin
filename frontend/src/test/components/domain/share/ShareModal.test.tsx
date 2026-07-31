import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ShareModal from '@/components/domain/share/ShareModal';
import { fetchMembers, fetchMyWorkspaces, updateWorkspace } from '@/services/workspaces';
import type { WorkspaceMember } from '@/services/workspaces';

// 검증 대상: 역할별 UI 분기(삭제하기/나가기·연필) + 이름 변경 호출
vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  fetchMyWorkspaces: vi.fn(),
  fetchMembers: vi.fn(),
  updateWorkspace: vi.fn(),
}));
// 내 userId 를 고정한다 — 역할 판단은 멤버 목록에서 나를 찾아 하므로 이 값이 기준점
vi.mock('@/hooks/useUser', () => ({ useUser: () => ({ userId: 1 }) }));

const members = (myRole: 'OWNER' | 'MEMBER'): WorkspaceMember[] => [
  {
    userId: 1,
    nickname: '우현',
    email: 'a@x.com',
    role: myRole,
    joinedAt: '',
    avatarColor: 'BLUE',
  },
  {
    userId: 2,
    nickname: '지수',
    email: 'b@x.com',
    role: myRole === 'OWNER' ? 'MEMBER' : 'OWNER',
    joinedAt: '',
    avatarColor: 'PINK',
  },
];

const renderModal = (onRequestDelete = vi.fn()) =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter>
        <ShareModal workspaceId={3} onClose={() => {}} onRequestDelete={onRequestDelete} />
      </MemoryRouter>
    </QueryClientProvider>,
  );

beforeEach(() => {
  vi.mocked(fetchMyWorkspaces)
    .mockReset()
    .mockResolvedValue([{ id: 3, name: '우주 탐험대', type: 'TEAM', role: 'OWNER' }]);
  vi.mocked(fetchMembers).mockReset().mockResolvedValue(members('OWNER'));
  vi.mocked(updateWorkspace)
    .mockReset()
    .mockResolvedValue({ id: 3, name: '새 이름', type: 'TEAM', role: 'OWNER' });
});

describe('ShareModal', () => {
  it('OWNER 에겐 삭제하기가 뜨고, 누르면 삭제 요청 콜백을 부른다', async () => {
    const onRequestDelete = vi.fn();
    const { getByText } = await renderModal(onRequestDelete);
    await expect.poll(() => getByText('삭제하기').query()).not.toBeNull();

    await userEvent.click(getByText('삭제하기'));
    expect(onRequestDelete).toHaveBeenCalledOnce();
  });

  it('MEMBER 에겐 삭제하기 대신 나가기가 뜨고, 연필도 없다', async () => {
    vi.mocked(fetchMembers).mockResolvedValue(members('MEMBER'));
    const { container, getByText } = await renderModal();
    await expect.poll(() => getByText('나가기').query()).not.toBeNull();
    expect(getByText('삭제하기').query()).toBeNull();
    expect(container.querySelector('button[aria-label="이름 변경"]')).toBeNull();
  });

  it('연필 → 이름 수정 → 저장하면 이름 변경 API 를 부른다', async () => {
    const { container } = await renderModal();
    await expect
      .poll(() => container.querySelector('button[aria-label="이름 변경"]'))
      .not.toBeNull();

    await userEvent.click(container.querySelector('button[aria-label="이름 변경"]')!);
    const input = container.querySelector(
      'input[aria-label="워크스페이스 이름"]',
    ) as HTMLInputElement;
    expect(input.value).toBe('우주 탐험대'); // 현재 이름이 채워진 채 시작

    await userEvent.fill(input, '새 이름');
    await userEvent.click(container.querySelector('button[aria-label="저장"]')!);
    await expect.poll(() => vi.mocked(updateWorkspace).mock.calls.length).toBe(1);
    expect(updateWorkspace).toHaveBeenCalledWith(3, '새 이름');
  });

  it('이름을 그대로 두고 저장하면 API 를 부르지 않고 닫는다', async () => {
    const { container } = await renderModal();
    await expect
      .poll(() => container.querySelector('button[aria-label="이름 변경"]'))
      .not.toBeNull();

    await userEvent.click(container.querySelector('button[aria-label="이름 변경"]')!);
    await userEvent.click(container.querySelector('button[aria-label="저장"]')!);

    expect(updateWorkspace).not.toHaveBeenCalled();
    // 편집이 닫히고 보기 모드로 돌아온다
    expect(container.querySelector('input[aria-label="워크스페이스 이름"]')).toBeNull();
  });
});
