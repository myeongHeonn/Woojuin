import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import CategoryEditor from '@/components/domain/library/detail/CategoryEditor';
import type { ItemDetail } from '@/types/item';
import type { Category } from '@/types/category';

const { updateMutate } = vi.hoisted(() => ({ updateMutate: vi.fn() }));
vi.mock('@/hooks/useItemActions', () => ({ useUpdateItem: () => ({ mutate: updateMutate }) }));

// 워크스페이스 전체 카테고리 — 1·2·3 있고 아이템엔 1·2 만 붙어 있다
const ALL: Category[] = [
  { categoryId: 1, name: '여행', color: '#7F77DD' },
  { categoryId: 2, name: '디자인', color: '#1D9E75' },
  { categoryId: 3, name: '요리', color: '#EF9F27' },
];
vi.mock('@/hooks/useCategories', () => ({ useCategories: () => ({ data: ALL }) }));

const item = {
  itemId: 5,
  type: 'MEMO',
  status: 'DONE',
  title: 't',
  url: null,
  content: null,
  summary: null,
  imageUrl: null,
  preview: { thumbnailUrl: null, description: null },
  categories: [ALL[0], ALL[1]],
  favorite: false,
  createdAt: '',
  deletedAt: null,
} as ItemDetail;

beforeEach(() => updateMutate.mockReset());

describe('CategoryEditor', () => {
  it('칩의 ✕ 로 그 카테고리를 뺀 집합을 보낸다', async () => {
    const { container } = await render(<CategoryEditor item={item} workspaceId={3} />);
    await userEvent.click(container.querySelector('button[aria-label="여행 제거"]')!);
    expect(updateMutate).toHaveBeenCalledWith({ categoryIds: [2] });
  });

  it('추가 피커엔 아직 없는 카테고리(요리)만 나오고, 고르면 더한 집합을 보낸다', async () => {
    const { container } = await render(<CategoryEditor item={item} workspaceId={3} />);
    await userEvent.click(
      [...container.querySelectorAll('button')].find((b) => b.textContent?.includes('카테고리'))!,
    );

    const menuItems = [...container.querySelectorAll('[role="menuitem"]')];
    expect(menuItems).toHaveLength(1);
    expect(menuItems[0].textContent).toContain('요리');

    await userEvent.click(menuItems[0] as HTMLElement);
    expect(updateMutate).toHaveBeenCalledWith({ categoryIds: [1, 2, 3] });
  });
});
