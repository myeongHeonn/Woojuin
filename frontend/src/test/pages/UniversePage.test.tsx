import { flushSync } from 'react-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UniversePage from '@/pages/UniversePage';
import { searchItems } from '@/services/items';
import { fetchUniverse } from '@/services/universe';
import type { SceneCallbacks, StarNode, UniverseScene } from '@/utils/scene';
import type { UniverseResponse } from '@/types/universe';
import type { Item, ItemAiSearchResponse } from '@/types/item';

/** 씬은 WebGL 이라 갈아끼운다 — 여기서 볼 건 "무엇을 씬에 알렸는가" 뿐이다 */
let sceneCallbacks: SceneCallbacks | undefined;
let scene: UniverseScene | undefined;

vi.mock('@/utils/scene', () => ({
  createUniverseScene: (_canvas: HTMLCanvasElement, callbacks: SceneCallbacks): UniverseScene => {
    sceneCallbacks = callbacks;
    scene = {
      focusOn: vi.fn(),
      setHighlightedItems: vi.fn(),
      setActiveCategory: vi.fn(),
      setPointerOverTooltip: vi.fn(),
      getCameraState: vi.fn().mockReturnValue({ rotX: 0, rotY: 0, camZ: 62 }),
      dispose: vi.fn(),
    };
    return scene;
  },
}));

vi.mock('@/services/universe', () => ({ fetchUniverse: vi.fn() }));
vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  getCategories: vi.fn().mockResolvedValue([]),
}));
vi.mock('@/services/items', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/items')>()),
  fetchItems: vi.fn(),
  searchItems: vi.fn(),
  aiSearchItems: vi.fn(),
}));

const universe: UniverseResponse = {
  constellations: [
    {
      categoryId: 1,
      categoryName: '여행',
      color: 0x8fb4ff,
      items: [{ id: 10, position: [1, 2, 3], title: '서울', type: 'URL', url: 'https://a' }],
    },
  ],
  unclassified: [],
};

const hubNode: StarNode = {
  hub: {
    categoryId: 1,
    name: '여행',
    color: 0x8fb4ff,
    position: [0, 0, 0],
    radius: 2,
    itemCount: 1,
  },
  isHub: true,
  categoryNames: ['여행'],
  cssColor: '#8fb4ff',
};

const item: Item = {
  itemId: 10,
  type: 'URL',
  status: 'DONE',
  title: '서울',
  url: 'https://a',
  summary: null,
  preview: { thumbnailUrl: null, description: null },
  imageUrl: null,
  categories: [],
  favorite: false,
  createdAt: '2026-01-01T00:00:00Z',
  deletedAt: null,
};

const searchPage = (content: Item[]): ItemAiSearchResponse => ({
  content,
  page: 0,
  size: 28,
  totalElements: content.length,
  partialMatch: false,
  interpretedQuery: '',
  aiPlanned: false,
});

beforeEach(async () => {
  sceneCallbacks = undefined;
  scene = undefined;
  vi.mocked(fetchUniverse).mockResolvedValue(universe);
  const items = await import('@/services/items');
  vi.mocked(items.fetchItems).mockResolvedValue(searchPage([]));
  vi.mocked(searchItems)
    .mockReset()
    .mockResolvedValue(searchPage([item]));
});

const renderPage = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={['/workspace/3/universe']}>
        <Routes>
          <Route path="/workspace/:workspaceId/universe" element={<UniversePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

/**
 * 카테고리 강조는 소속 밖의 별을 어둡게 만든다. 검색 결과가 다른 별자리에 있으면
 * 그 어둠에 묻혀 검색이 제 역할을 못 하므로, 검색하는 순간 카테고리 강조를 끈다.
 * 기준은 결과 목록이 아니라 확정된 검색어다 — 0건도 "검색한 것"이다.
 */
describe('UniversePage — 검색과 카테고리 강조', () => {
  it('검색 결과가 뜨면 카테고리 강조를 끈다', async () => {
    const { container } = await renderPage();
    await expect.poll(() => sceneCallbacks).toBeDefined();

    // 별자리 중심을 골라 카테고리를 활성화한다
    flushSync(() => sceneCallbacks!.onSelect(hubNode, { x: 0, y: 0, visible: true }));
    await expect.poll(() => vi.mocked(scene!.setActiveCategory).mock.calls.at(-1)?.[0]).toBe(1);

    await userEvent.fill(
      container.querySelector('input[aria-label="검색어"]') as HTMLInputElement,
      '서울',
    );
    await userEvent.keyboard('{Enter}');

    // 검색 결과가 하이라이트되는 동안 카테고리 강조는 꺼져 있어야 한다
    await expect
      .poll(() => vi.mocked(scene!.setHighlightedItems).mock.calls.at(-1)?.[0])
      .toEqual([10]);
    expect(vi.mocked(scene!.setActiveCategory).mock.calls.at(-1)?.[0]).toBeNull();
  });

  /**
   * 0건이어도 꺼야 한다 — 어두워진 화면이 남으면 "왜 안 나오지"가 아니라
   * "왜 다 어둡지"가 된다. 그래서 결과 목록이 아니라 검색어를 기준으로 판단한다.
   */
  it('검색 결과가 0건이어도 카테고리 강조를 끈다', async () => {
    vi.mocked(searchItems).mockResolvedValue(searchPage([]));
    const { container } = await renderPage();
    await expect.poll(() => sceneCallbacks).toBeDefined();

    flushSync(() => sceneCallbacks!.onSelect(hubNode, { x: 0, y: 0, visible: true }));
    await expect.poll(() => vi.mocked(scene!.setActiveCategory).mock.calls.at(-1)?.[0]).toBe(1);

    await userEvent.fill(
      container.querySelector('input[aria-label="검색어"]') as HTMLInputElement,
      '없는말',
    );
    await userEvent.keyboard('{Enter}');

    await expect.poll(() => container.textContent).toContain('검색 결과가 없어요');
    expect(vi.mocked(scene!.setActiveCategory).mock.calls.at(-1)?.[0]).toBeNull();
  });

  // 마운트 직후에도 이 콜백은 빈 검색어로 한 번 불린다 — 그때 강조가 꺼지면 안 된다
  it('검색하지 않았으면 카테고리 강조를 유지한다', async () => {
    await renderPage();
    await expect.poll(() => sceneCallbacks).toBeDefined();

    flushSync(() => sceneCallbacks!.onSelect(hubNode, { x: 0, y: 0, visible: true }));
    await expect.poll(() => vi.mocked(scene!.setActiveCategory).mock.calls.at(-1)?.[0]).toBe(1);

    expect(vi.mocked(scene!.setActiveCategory).mock.calls.at(-1)?.[0]).toBe(1);
  });
});
