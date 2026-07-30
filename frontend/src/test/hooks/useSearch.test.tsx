import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSearch } from '@/hooks/useSearch';
import { searchItems, aiSearchItems } from '@/services/items';
import type { ItemAiSearchResponse } from '@/types/item';

// 검증 대상은 "aiMode 로 어느 엔드포인트를 부르나 · q 없으면 안 부르나"
vi.mock('@/services/items', () => ({ searchItems: vi.fn(), aiSearchItems: vi.fn() }));
const mockSearch = vi.mocked(searchItems);
const mockAi = vi.mocked(aiSearchItems);

const emptyPage = (): ItemAiSearchResponse => ({
  content: [],
  page: 0,
  size: 28,
  totalElements: 0,
  partialMatch: false,
  interpretedQuery: '',
  aiPlanned: true,
});

function Probe({ q, ai }: { q: string; ai: boolean }) {
  const { items } = useSearch(3, q, ai);
  return <span>{items.length}</span>;
}

const wrap = (ui: React.ReactNode) =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      {ui}
    </QueryClientProvider>,
  );

beforeEach(() => {
  mockSearch.mockReset().mockResolvedValue(emptyPage());
  mockAi.mockReset().mockResolvedValue(emptyPage());
});

describe('useSearch', () => {
  it('검색어가 비면 아무 요청도 하지 않는다', async () => {
    await wrap(<Probe q="   " ai={false} />);
    expect(mockSearch).not.toHaveBeenCalled();
    expect(mockAi).not.toHaveBeenCalled();
  });

  it('일반 모드는 searchItems 만 부른다', async () => {
    await wrap(<Probe q="파스타" ai={false} />);
    await expect.poll(() => mockSearch.mock.calls.length).toBe(1);
    expect(mockAi).not.toHaveBeenCalled();
  });

  it('AI 모드는 aiSearchItems 만 부른다', async () => {
    await wrap(<Probe q="파스타" ai />);
    await expect.poll(() => mockAi.mock.calls.length).toBe(1);
    expect(mockSearch).not.toHaveBeenCalled();
  });
});
