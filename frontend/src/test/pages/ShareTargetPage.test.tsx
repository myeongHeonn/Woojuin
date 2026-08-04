import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider, createStore } from 'jotai';
import ShareTargetPage from '@/pages/ShareTargetPage';
import { accessTokenAtom, postLoginRedirectAtom } from '@/stores/authAtoms';
import { lastShareSpaceIdAtom } from '@/stores/shareAtoms';
import { stubApi } from '@/test/helpers/stubApi';

const get = stubApi('get');
const post = stubApi('post');

const WORKSPACES = [
  { id: 1, name: 'My Space', type: 'PERSONAL', role: 'OWNER' },
  { id: 7, name: '몽골 여행', type: 'TEAM', role: 'OWNER' },
];

const renderShare = async (
  query: string,
  {
    token = 'token',
    lastSpaceId = null,
  }: { token?: string | null; lastSpaceId?: number | null } = {},
) => {
  const store = createStore();
  store.set(accessTokenAtom, token);
  store.set(lastShareSpaceIdAtom, lastSpaceId);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = await render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[`/share-target${query}`]}>
          <Routes>
            <Route path="/share-target" element={<ShareTargetPage />} />
            <Route path="/login" element={<div>로그인 화면</div>} />
            <Route path="/workspace/:id/library" element={<div>보관함</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  );
  return { ...result, store };
};

/** 워크스페이스 목록 응답 — 서비스가 res.data.data 를 읽는다 */
const workspacesOk = () => get.mockResolvedValue({ data: { data: WORKSPACES } });

describe('ShareTargetPage', () => {
  it('공유된 링크를 보여 주고 저장 위치를 고를 수 있다', async () => {
    workspacesOk();
    const { container } = await renderShare('?url=https://example.com/a');

    await vi.waitFor(() => {
      expect(container.textContent).toContain('https://example.com/a');
    });
    // 기억한 위치가 없으면 개인 스페이스가 기본
    await vi.waitFor(() => {
      expect(container.textContent).toContain('Personal Space');
    });
  });

  it('기억한 저장 위치를 기본으로 고른다', async () => {
    // 공유는 "던져 놓고 나가기"라 매번 고르게 하면 느려진다
    workspacesOk();
    const { container } = await renderShare('?url=https://example.com/a', { lastSpaceId: 7 });

    await vi.waitFor(() => {
      expect(container.textContent).toContain('몽골 여행');
    });
  });

  it('기억한 위치가 목록에 없으면 개인 스페이스로 떨어진다', async () => {
    // 탈퇴·추방된 워크스페이스 id 가 남아 있을 수 있다
    workspacesOk();
    const { container } = await renderShare('?url=https://example.com/a', { lastSpaceId: 999 });

    await vi.waitFor(() => {
      expect(container.textContent).toContain('Personal Space');
    });
  });

  it('저장하면 고른 곳으로 URL 을 보내고 그 위치를 기억한다', async () => {
    workspacesOk();
    post.mockResolvedValue({ data: { data: { itemId: 10, status: 'PROCESSING' } } });
    const { container, store } = await renderShare('?url=https://example.com/a');

    await vi.waitFor(() => {
      expect(container.textContent).toContain('Personal Space');
    });
    const saveButton = [...container.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '저장하기',
    )!;
    saveButton.click();

    await vi.waitFor(() => {
      expect(container.textContent).toContain('저장했어요');
    });
    expect(post).toHaveBeenCalledWith('/workspaces/1/items', {
      type: 'URL',
      url: 'https://example.com/a',
    });
    expect(store.get(lastShareSpaceIdAtom)).toBe(1);
  });

  it('URL 이 없으면 메모로 저장한다', async () => {
    workspacesOk();
    post.mockResolvedValue({ data: { data: { itemId: 11, status: 'PROCESSING' } } });
    const { container } = await renderShare('?text=내일 장 볼 것');

    await vi.waitFor(() => {
      expect(container.textContent).toContain('Personal Space');
    });
    [...container.querySelectorAll('button')]
      .find((button) => button.textContent?.trim() === '저장하기')!
      .click();

    await vi.waitFor(() => {
      expect(post).toHaveBeenCalledWith('/workspaces/1/items', {
        type: 'MEMO',
        content: '내일 장 볼 것',
      });
    });
  });

  it('저장이 실패하면 이유를 보여 주고 다시 시도할 수 있다', async () => {
    workspacesOk();
    post.mockRejectedValue(new Error('boom'));
    const { container } = await renderShare('?url=https://example.com/a');

    await vi.waitFor(() => {
      expect(container.textContent).toContain('Personal Space');
    });
    [...container.querySelectorAll('button')]
      .find((button) => button.textContent?.trim() === '저장하기')!
      .click();

    await vi.waitFor(() => {
      expect(container.textContent).toContain('다시 시도');
    });
    // 공유 내용이 화면에 남아 있어야 다시 눌러 저장할 수 있다
    expect(container.textContent).toContain('https://example.com/a');
  });

  it('비로그인으로 들어오면 공유 내용을 담아 두고 로그인으로 보낸다', async () => {
    // 이걸 빠뜨리면 로그인 뒤 빈 화면으로 돌아와 사용자가 공유를 다시 해야 한다
    const { container, store } = await renderShare('?url=https://example.com/a', { token: null });

    await vi.waitFor(() => {
      expect(store.get(postLoginRedirectAtom)).toBe('/share-target?url=https://example.com/a');
    });
    expect(container.textContent).toContain('로그인하고 저장하기');
    // 무엇이 저장될지는 로그인 전에도 보여 준다
    expect(container.textContent).toContain('https://example.com/a');
  });

  it('공유된 내용이 없으면 저장 버튼 대신 안내를 준다', async () => {
    const { container } = await renderShare('');

    await vi.waitFor(() => {
      expect(container.textContent).toContain('공유된 내용을 읽지 못했어요');
    });
    expect(
      [...container.querySelectorAll('button')].some((b) => b.textContent?.includes('저장하기')),
    ).toBe(false);
  });
});
