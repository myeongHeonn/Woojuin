import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DeleteWorkspaceModal from '@/components/domain/share/DeleteWorkspaceModal';
import {
  deleteWorkspace,
  fetchMembers,
  fetchMyWorkspaces,
  getCategories,
} from '@/services/workspaces';
import { fetchItems } from '@/services/items';

// 영향 요약(아이템·카테고리·멤버 수)과 이름 확인이 검증 대상 — 네트워크는 전부 갈아끼운다
vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  fetchMyWorkspaces: vi.fn(),
  fetchMembers: vi.fn(),
  getCategories: vi.fn(),
  deleteWorkspace: vi.fn(),
}));
vi.mock('@/services/items', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/items')>()),
  fetchItems: vi.fn(),
}));

const renderModal = (open = true, onClose = () => {}) =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter>
        <DeleteWorkspaceModal workspaceId={3} open={open} onClose={onClose} />
      </MemoryRouter>
    </QueryClientProvider>,
  );

const input = (c: HTMLElement) =>
  c.querySelector('input[aria-label="워크스페이스 이름 확인"]') as HTMLInputElement;
const delBtn = (c: HTMLElement) => c.querySelector('button[type="submit"]') as HTMLButtonElement;

beforeEach(() => {
  vi.mocked(fetchMyWorkspaces)
    .mockReset()
    .mockResolvedValue([{ id: 3, name: '우주 탐험대', type: 'TEAM', role: 'OWNER' }]);
  vi.mocked(fetchItems)
    .mockReset()
    .mockResolvedValue({ content: [], page: 0, size: 1, totalElements: 128 });
  vi.mocked(getCategories)
    .mockReset()
    .mockResolvedValue([
      { categoryId: 1, name: '맛집', color: '#fff' },
      { categoryId: 2, name: '기타', color: '#eee' },
    ]);
  vi.mocked(fetchMembers)
    .mockReset()
    .mockResolvedValue([
      {
        userId: 1,
        nickname: '우현',
        email: 'a@x.com',
        role: 'OWNER',
        joinedAt: '',
        avatarColor: 'BLUE',
      },
      {
        userId: 2,
        nickname: '지수',
        email: 'b@x.com',
        role: 'MEMBER',
        joinedAt: '',
        avatarColor: 'PINK',
      },
    ]);
  vi.mocked(deleteWorkspace).mockReset().mockResolvedValue(undefined);
});

describe('DeleteWorkspaceModal', () => {
  it('무엇을 잃는지 요약을 보여준다', async () => {
    const { container } = await renderModal();
    await expect.poll(() => container.textContent).toContain('128개');
    expect(container.textContent).toContain('2개');
    expect(container.textContent).toContain('2명');
  });

  it('이름이 정확히 일치할 때만 삭제 버튼이 열린다', async () => {
    const { container } = await renderModal();
    expect(delBtn(container).disabled).toBe(true);

    await userEvent.fill(input(container), '우주 탐험');
    expect(delBtn(container).disabled).toBe(true);

    await userEvent.fill(input(container), '우주 탐험대');
    await expect.poll(() => delBtn(container).disabled).toBe(false);
  });

  it('확인 후 제출하면 삭제 API 를 부른다', async () => {
    const { container } = await renderModal();
    await userEvent.fill(input(container), '우주 탐험대');
    await expect.poll(() => delBtn(container).disabled).toBe(false);

    await userEvent.click(delBtn(container));
    await expect.poll(() => vi.mocked(deleteWorkspace).mock.calls.length).toBe(1);
    expect(deleteWorkspace).toHaveBeenCalledWith(3);
  });

  it('닫혀 있으면 아무것도 그리지 않고 영향 조회도 하지 않는다', async () => {
    const { container } = await renderModal(false);
    expect(container.querySelector('form')).toBeNull();
    expect(fetchItems).not.toHaveBeenCalled();
  });
});
