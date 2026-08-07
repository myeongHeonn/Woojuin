import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useItems, processingPollInterval, processingItemCount } from '@/hooks/useItems';
import { fetchItems } from '@/services/items';
import type { Item, ItemListResponse, ItemStatus } from '@/types/item';

// 서버는 막는다 — 검증 대상은 getNextPageParam(다음 페이지 있나) 판단이지 통신이 아니다
vi.mock('@/services/items', () => ({ fetchItems: vi.fn() }));
const mockFetch = vi.mocked(fetchItems);

const SIZE = 20;

/** id 만 있는 최소 아이템 — 개수만 세면 되므로 나머지는 캐스팅으로 생략 */
const item = (id: number) => ({ itemId: id }) as Item;

/** 한 페이지 응답을 만든다 */
const pageOf = (ids: number[], page: number, totalElements: number): ItemListResponse => ({
  content: ids.map(item),
  page,
  size: SIZE,
  totalElements,
});

const range = (from: number, count: number) => Array.from({ length: count }, (_, i) => from + i);

/** 상태가 있는 최소 아이템 — 폴링 판단(processingPollInterval) 검증용 */
const withStatus = (status: ItemStatus) => ({ itemId: 1, status }) as Item;
const pageWith = (...statuses: ItemStatus[]): ItemListResponse => ({
  content: statuses.map(withStatus),
  page: 0,
  size: SIZE,
  totalElements: statuses.length,
});

/** useItems 를 부르고 값을 DOM 에 노출하는 프로브 — 훅만 테스트하려고 얇게 둔다 */
function Probe() {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useItems({
    workspaceId: 3,
    size: SIZE,
  });
  const items = data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <div>
      <span data-testid="count">{items.length}</span>
      <span data-testid="hasNext">{String(hasNextPage)}</span>
      <button onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
        more
      </button>
    </div>
  );
}

const renderProbe = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <Probe />
    </QueryClientProvider>,
  );

const countOf = (c: HTMLElement) => c.querySelector('[data-testid="count"]')?.textContent;
const hasNextOf = (c: HTMLElement) => c.querySelector('[data-testid="hasNext"]')?.textContent;

beforeEach(() => mockFetch.mockReset());

describe('useItems 무한 스크롤', () => {
  it('아이템이 없으면 빈 목록이고 다음 페이지가 없다', async () => {
    mockFetch.mockResolvedValue(pageOf([], 0, 0));
    const { container } = await renderProbe();

    await expect.poll(() => countOf(container)).toBe('0');
    // (0+1)*20=20 < 0 → 거짓 → getNextPageParam 이 undefined → hasNextPage false
    expect(hasNextOf(container)).toBe('false');
  });

  it('1페이지를 다 못 채우면(총량 < size) 다음 페이지가 없다', async () => {
    // 5개뿐 — 첫 페이지에서 끝이다
    mockFetch.mockResolvedValue(pageOf(range(1, 5), 0, 5));
    const { container } = await renderProbe();

    await expect.poll(() => countOf(container)).toBe('5');
    // (0+1)*20=20 < 5 → 거짓 → 더 없음
    expect(hasNextOf(container)).toBe('false');
    // 첫 페이지만 받았어야 한다
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  describe('정상 흐름 — 총 45개, 20씩', () => {
    beforeEach(() => {
      // 페이지 번호에 맞는 응답을 돌려준다
      mockFetch.mockImplementation((args) => {
        const page = args?.page ?? 0;
        if (page === 0) return Promise.resolve(pageOf(range(1, 20), 0, 45));
        if (page === 1) return Promise.resolve(pageOf(range(21, 20), 1, 45));
        return Promise.resolve(pageOf(range(41, 5), 2, 45));
      });
    });

    it('첫 페이지는 20개이고 다음 페이지가 있다', async () => {
      const { container } = await renderProbe();

      await expect.poll(() => countOf(container)).toBe('20');
      // (0+1)*20=20 < 45 → 참 → 다음 페이지 1
      expect(hasNextOf(container)).toBe('true');
    });

    it('fetchNextPage 로 다음 페이지가 이어붙는다', async () => {
      const { container } = await renderProbe();
      await expect.poll(() => countOf(container)).toBe('20');

      await userEvent.click(container.querySelector('button')!);

      await expect.poll(() => countOf(container)).toBe('40');
      // (1+1)*20=40 < 45 → 아직 더 있음
      expect(hasNextOf(container)).toBe('true');
    });

    it('마지막 페이지까지 가면 다음 페이지가 없어진다', async () => {
      const { container } = await renderProbe();
      await expect.poll(() => countOf(container)).toBe('20');

      const more = container.querySelector('button')!;
      await userEvent.click(more); // → 40
      await expect.poll(() => countOf(container)).toBe('40');
      await userEvent.click(more); // → 45

      await expect.poll(() => countOf(container)).toBe('45');
      // (2+1)*20=60 < 45 → 거짓 → 끝
      await expect.poll(() => hasNextOf(container)).toBe('false');
      // 0·1·2 세 페이지만 받았어야 한다
      expect(mockFetch).toHaveBeenCalledTimes(3);
    });
  });
});

describe('processingPollInterval — 처리 중일 때만 폴링', () => {
  it('PROCESSING 이 하나라도 있으면 간격(ms)을 돌려준다', () => {
    expect(processingPollInterval([pageWith('DONE', 'PROCESSING')], 3000)).toBe(3000);
  });

  it('다른 페이지에 PROCESSING 이 있어도 잡는다', () => {
    expect(processingPollInterval([pageWith('DONE'), pageWith('PROCESSING')], 3000)).toBe(3000);
  });

  it('전부 완료면 false — 폴링 정지', () => {
    // DONE·PARTIAL·FAILED 는 더 안 바뀌므로 폴링할 이유가 없다
    expect(processingPollInterval([pageWith('DONE', 'PARTIAL', 'FAILED')])).toBe(false);
  });

  it('데이터가 아직 없으면 false', () => {
    expect(processingPollInterval(undefined)).toBe(false);
  });
});

describe('processingItemCount — 처리 중인 아이템 개수', () => {
  it('여러 페이지에 걸친 PROCESSING 을 모두 센다', () => {
    expect(processingItemCount([pageWith('PROCESSING', 'DONE'), pageWith('PROCESSING')])).toBe(2);
  });

  it('전부 완료면 0', () => {
    expect(processingItemCount([pageWith('DONE', 'PARTIAL', 'FAILED')])).toBe(0);
  });

  it('데이터가 아직 없으면 0', () => {
    expect(processingItemCount(undefined)).toBe(0);
  });
});
