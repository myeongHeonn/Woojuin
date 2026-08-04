import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import ItemActions from '@/components/domain/library/detail/ItemActions';
import type { ItemDetail } from '@/types/item';

// 뮤테이션은 스파이로 주입 — 검증 대상은 "무엇을 어떤 값으로 부르나"(동작 배선)
const { favoriteMutate, deleteMutate } = vi.hoisted(() => ({
  favoriteMutate: vi.fn(),
  deleteMutate: vi.fn(),
}));
vi.mock('@/hooks/useItemActions', () => ({
  useSetFavorite: () => ({ mutate: favoriteMutate }),
  useDeleteItem: () => ({ mutate: deleteMutate }),
}));

const make = (over: Partial<ItemDetail> = {}): ItemDetail => ({
  itemId: 9,
  type: 'MEMO',
  status: 'DONE',
  title: '메모',
  url: null,
  content: '본문',
  summary: null,
  imageUrl: null,
  preview: { thumbnailUrl: null, description: null },
  categories: [],
  favorite: false,
  createdAt: new Date().toISOString(),
  deletedAt: null,
  ...over,
});

const btn = (c: HTMLElement, label: string) =>
  c.querySelector(`button[aria-label="${label}"]`) as HTMLElement;

beforeEach(() => {
  favoriteMutate.mockReset();
  deleteMutate.mockReset();
});

describe('ItemActions', () => {
  it('즐겨찾기·삭제를 (드롭다운 없이) 바로 눌리는 아이콘 버튼으로 노출한다', async () => {
    const { container } = await render(<ItemActions item={make()} onDeleted={() => {}} />);
    // ⋮ 더보기 단계 없이 바로 두 버튼이 보인다 — 닫기(✕)와 같은 도달성
    expect(container.querySelector('button[aria-label="더보기"]')).toBeNull();
    expect(btn(container, '즐겨찾기')).not.toBeNull();
    expect(btn(container, '삭제')).not.toBeNull();
  });

  it('즐겨찾기가 꺼져 있으면 "즐겨찾기" 버튼이고, 누르면 favorite:true 로 수정한다', async () => {
    const { container } = await render(
      <ItemActions item={make({ favorite: false })} onDeleted={() => {}} />,
    );
    await userEvent.click(btn(container, '즐겨찾기'));
    expect(favoriteMutate).toHaveBeenCalledWith(true);
  });

  it('이미 즐겨찾기면 "즐겨찾기 해제" 버튼이고, 누르면 favorite:false 로 수정한다', async () => {
    const { container } = await render(
      <ItemActions item={make({ favorite: true })} onDeleted={() => {}} />,
    );
    expect(btn(container, '즐겨찾기')).toBeNull();
    await userEvent.click(btn(container, '즐겨찾기 해제'));
    expect(favoriteMutate).toHaveBeenCalledWith(false);
  });

  it('삭제를 누르면 그 아이템 id 로 삭제를 부른다', async () => {
    const { container } = await render(
      <ItemActions item={make({ itemId: 42 })} onDeleted={() => {}} />,
    );
    await userEvent.click(btn(container, '삭제'));
    expect(deleteMutate).toHaveBeenCalledWith(42, expect.anything());
  });
});
