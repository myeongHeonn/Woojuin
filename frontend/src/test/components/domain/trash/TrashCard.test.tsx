import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import TrashCard from '@/components/domain/trash/TrashCard';
import type { Item } from '@/types/item';

const item = {
  itemId: 5,
  type: 'URL',
  status: 'DONE',
  title: '지워진 링크',
  url: null,
  summary: null,
  imageUrl: null,
  preview: { thumbnailUrl: null, description: null },
  categories: [],
  favorite: false,
  createdAt: '',
  deletedAt: null,
} as Item;

const menuItem = (c: HTMLElement, text: string) =>
  [...c.querySelectorAll('[role="menuitem"]')].find((b) =>
    b.textContent?.includes(text),
  ) as HTMLElement;

const openMenu = (c: HTMLElement) => userEvent.click(c.querySelector('button')!);

describe('TrashCard', () => {
  it('카드를 누르면 복구·영구 삭제 메뉴가 뜬다', async () => {
    const { container } = await render(
      <TrashCard item={item} onRestore={() => {}} onDelete={() => {}} />,
    );
    await openMenu(container);
    expect(menuItem(container, '복구')).toBeTruthy();
    expect(menuItem(container, '영구 삭제')).toBeTruthy();
  });

  it('복구는 onRestore, 영구 삭제는 onDelete 를 부른다', async () => {
    const onRestore = vi.fn();
    const onDelete = vi.fn();
    const { container } = await render(
      <TrashCard item={item} onRestore={onRestore} onDelete={onDelete} />,
    );

    await openMenu(container);
    await userEvent.click(menuItem(container, '복구'));
    expect(onRestore).toHaveBeenCalledOnce();

    await openMenu(container);
    await userEvent.click(menuItem(container, '영구 삭제'));
    expect(onDelete).toHaveBeenCalledOnce();
  });
});
