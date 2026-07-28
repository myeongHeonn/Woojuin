import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import ItemActionsMenu from '@/components/domain/library/detail/ItemActionsMenu';
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

const openMenu = async (c: HTMLElement) =>
  userEvent.click(c.querySelector('button[aria-label="더보기"]')!);
const itemByText = (c: HTMLElement, text: string) =>
  [...c.querySelectorAll('[role="menuitem"]')].find((b) =>
    b.textContent?.includes(text),
  ) as HTMLElement;

beforeEach(() => {
  favoriteMutate.mockReset();
  deleteMutate.mockReset();
});

describe('ItemActionsMenu', () => {
  it('즐겨찾기가 꺼져 있으면 켜는 항목을 보여주고, 누르면 favorite:true 로 수정한다', async () => {
    const { container } = await render(
      <ItemActionsMenu item={make({ favorite: false })} onDeleted={() => {}} />,
    );
    await openMenu(container);
    const fav = itemByText(container, '즐겨찾기');
    expect(fav.textContent).toContain('즐겨찾기');
    expect(fav.textContent).not.toContain('해제');

    await userEvent.click(fav);
    expect(favoriteMutate).toHaveBeenCalledWith(true);
  });

  it('이미 즐겨찾기면 "해제" 항목이고, 누르면 favorite:false 로 수정한다', async () => {
    const { container } = await render(
      <ItemActionsMenu item={make({ favorite: true })} onDeleted={() => {}} />,
    );
    await openMenu(container);
    const fav = itemByText(container, '해제');
    await userEvent.click(fav);
    expect(favoriteMutate).toHaveBeenCalledWith(false);
  });

  it('삭제를 누르면 그 아이템 id 로 삭제를 부른다', async () => {
    const { container } = await render(
      <ItemActionsMenu item={make({ itemId: 42 })} onDeleted={() => {}} />,
    );
    await openMenu(container);
    await userEvent.click(itemByText(container, '삭제'));
    expect(deleteMutate).toHaveBeenCalledWith(42, expect.anything());
  });
});
