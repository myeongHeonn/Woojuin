import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import ItemModal from '@/components/domain/library/detail/ItemModal';
import { useItem } from '@/hooks/useItem';
import type { ItemDetail, ItemType } from '@/types/item';

// 데이터는 훅을 막아 주입한다 — 검증 대상은 셸의 분기·닫기와 타입별 바디 표현이다
vi.mock('@/hooks/useItem', () => ({ useItem: vi.fn() }));
const mockUseItem = vi.mocked(useItem);

// 액션 메뉴·카테고리 편집기가 쓰는 훅은 여기 관심사가 아니라 막는다(QueryClient 없이 렌더)
vi.mock('@/hooks/useCategories', () => ({ useCategories: () => ({ data: [] }) }));
vi.mock('@/hooks/useItemActions', () => ({
  useUpdateItem: () => ({ mutate: vi.fn() }),
  useSetFavorite: () => ({ mutate: vi.fn() }),
  useDeleteItem: () => ({ mutate: vi.fn() }),
}));

type QueryLike = ReturnType<typeof useItem>;
const loading = () => ({ data: undefined, isLoading: true, isError: false }) as QueryLike;
const errored = () => ({ data: undefined, isLoading: false, isError: true }) as QueryLike;
const loaded = (item: ItemDetail) =>
  ({ data: item, isLoading: false, isError: false }) as QueryLike;

const make = (type: ItemType, over: Partial<ItemDetail> = {}): ItemDetail => ({
  itemId: 1,
  type,
  status: 'DONE',
  title: '제목',
  url: null,
  content: null,
  summary: null,
  imageUrl: null,
  preview: { thumbnailUrl: null, description: null },
  categories: [],
  favorite: false,
  createdAt: new Date().toISOString(),
  deletedAt: null,
  ...over,
});

beforeEach(() => mockUseItem.mockReset());

describe('ItemModal', () => {
  it('itemId 가 null 이면 아무것도 그리지 않는다', async () => {
    mockUseItem.mockReturnValue(loading());
    const { container } = await render(
      <ItemModal workspaceId={3} itemId={null} onClose={() => {}} />,
    );
    expect(container.textContent).toBe('');
  });

  it('로딩 중이면 모달을 아예 띄우지 않는다', async () => {
    mockUseItem.mockReturnValue(loading());
    const { container } = await render(<ItemModal workspaceId={3} itemId={1} onClose={() => {}} />);
    expect(container.textContent).toBe('');
  });

  it('실패면 안내를 보여준다', async () => {
    mockUseItem.mockReturnValue(errored());
    const { container } = await render(<ItemModal workspaceId={3} itemId={1} onClose={() => {}} />);
    expect(container.textContent).toContain('불러오지 못했어요');
  });

  describe('타입별 바디', () => {
    it('IMAGE — 원본 이미지와 요약(summary)을 보여준다', async () => {
      mockUseItem.mockReturnValue(
        loaded(
          make('IMAGE', {
            title: '영수증',
            imageUrl: 'http://x/o.png',
            summary: '스타벅스 4500원 결제',
          }),
        ),
      );
      const { container } = await render(
        <ItemModal workspaceId={3} itemId={1} onClose={() => {}} />,
      );
      expect(container.querySelector('img')?.getAttribute('src')).toBe('http://x/o.png');
      expect(container.textContent).toContain('영수증');
      expect(container.textContent).toContain('스타벅스 4500원 결제');
      expect(container.textContent).toContain('요약');
    });

    it('URL — 원문 열기 버튼(url)과 요약을 보여준다', async () => {
      mockUseItem.mockReturnValue(
        loaded(make('URL', { url: 'https://ex.com/a', summary: '핵심 요약' })),
      );
      const { container } = await render(
        <ItemModal workspaceId={3} itemId={1} onClose={() => {}} />,
      );
      const link = container.querySelector('a');
      expect(link?.getAttribute('href')).toBe('https://ex.com/a');
      expect(link?.getAttribute('target')).toBe('_blank');
      expect(container.textContent).toContain('원문 열기');
      expect(container.textContent).toContain('핵심 요약');
    });

    it('MEMO — 본문 전문과 저장 시각을 보여준다', async () => {
      mockUseItem.mockReturnValue(
        loaded(make('MEMO', { title: '아이디어', content: '우주인 회의 메모\n두 번째 줄' })),
      );
      const { container } = await render(
        <ItemModal workspaceId={3} itemId={1} onClose={() => {}} />,
      );
      expect(container.textContent).toContain('아이디어');
      expect(container.textContent).toContain('우주인 회의 메모');
      expect(container.textContent).toContain('방금 전 저장');
    });
  });

  describe('닫기', () => {
    it('✕ 버튼으로 닫는다', async () => {
      const onClose = vi.fn();
      mockUseItem.mockReturnValue(loaded(make('MEMO')));
      const { container } = await render(
        <ItemModal workspaceId={3} itemId={1} onClose={onClose} />,
      );
      await userEvent.click(container.querySelector('button[aria-label="닫기"]')!);
      expect(onClose).toHaveBeenCalledOnce();
    });

    it('배경을 클릭하면 닫히고, 안쪽 카드는 닫히지 않는다', async () => {
      const onClose = vi.fn();
      mockUseItem.mockReturnValue(loaded(make('MEMO', { title: '카드안' })));
      const { container } = await render(
        <ItemModal workspaceId={3} itemId={1} onClose={onClose} />,
      );
      // 안쪽 카드(제목) 클릭 → 카드가 stopPropagation 하므로 안 닫힘
      await userEvent.click(container.querySelector('h2')!);
      expect(onClose).not.toHaveBeenCalled();
      // 배경(오버레이) 자체에 클릭 — 중앙은 카드가 덮고 있어 요소에 직접 디스패치한다
      (container.firstElementChild as HTMLElement).click();
      expect(onClose).toHaveBeenCalledOnce();
    });

    it('Esc 로 닫는다', async () => {
      const onClose = vi.fn();
      mockUseItem.mockReturnValue(loaded(make('MEMO')));
      await render(<ItemModal workspaceId={3} itemId={1} onClose={onClose} />);
      await userEvent.keyboard('{Escape}');
      expect(onClose).toHaveBeenCalledOnce();
    });
  });
});
