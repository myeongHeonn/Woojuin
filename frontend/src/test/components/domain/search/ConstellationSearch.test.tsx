import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ConstellationSearch from '@/components/domain/search/ConstellationSearch';
import { searchItems, aiSearchItems } from '@/services/items';
import type { ItemAiSearchResponse } from '@/types/item';

// 결과 패널의 열림/닫힘만 보므로 응답은 빈 페이지로 고정한다("검색 결과가 없어요"가 패널의 표식).
// 검색 두 함수만 갈아끼운다 — 같은 모듈의 fetchItem 등을 ItemModal 이 쓰고 있어 원본을 남겨야 한다
vi.mock('@/services/items', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/items')>()),
  searchItems: vi.fn(),
  aiSearchItems: vi.fn(),
}));
const mockSearch = vi.mocked(searchItems);

const emptyPage = (): ItemAiSearchResponse => ({
  content: [],
  page: 0,
  size: 28,
  totalElements: 0,
  partialMatch: false,
  interpretedQuery: '',
  aiPlanned: false,
});

const renderSearch = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={['/workspaces/3']}>
        <Routes>
          <Route path="/workspaces/:workspaceId" element={<ConstellationSearch />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

const input = (c: HTMLElement) => c.querySelector('input[aria-label="검색어"]') as HTMLInputElement;
const panelText = (c: HTMLElement) => c.textContent ?? '';

beforeEach(() => {
  mockSearch.mockReset().mockResolvedValue(emptyPage());
  vi.mocked(aiSearchItems).mockReset().mockResolvedValue(emptyPage());
});

describe('ConstellationSearch', () => {
  it('검색어를 제출하면 결과 패널이 뜬다', async () => {
    const { container } = await renderSearch();
    await userEvent.fill(input(container), '파스타');
    await userEvent.keyboard('{Enter}');
    await expect.poll(() => panelText(container)).toContain('검색 결과가 없어요');
  });

  // AiModeHint 는 토글 상태와 무관하게 항상 보인다 — 자리가 없어지면 검색창이
  // 위로 움직여 거슬리기 때문(AiModeHint.tsx 주석 참고)
  it('AI 모드를 켜도 권유 문구가 그대로 남는다', async () => {
    const { container } = await renderSearch();
    expect(panelText(container)).toContain('AI 모드로 바꿔보세요');

    await userEvent.click(container.querySelector('button[aria-label="AI 모드"]')!);
    await expect.poll(() => panelText(container)).toContain('AI 모드로 바꿔보세요');
  });

  it('입력을 다 지우면 결과 패널이 닫힌다', async () => {
    const { container } = await renderSearch();
    await userEvent.fill(input(container), '파스타');
    await userEvent.keyboard('{Enter}');
    await expect.poll(() => panelText(container)).toContain('검색 결과가 없어요');

    await userEvent.clear(input(container));
    await expect.poll(() => panelText(container)).not.toContain('검색 결과가 없어요');
  });
});
