import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import MemoBody from '@/components/domain/library/detail/MemoBody';
import type { ItemDetail } from '@/types/item';

// 저장 뮤테이션은 스파이로 주입 — 검증 대상은 "무엇을 저장하나"(제목·본문)
const { updateMutate } = vi.hoisted(() => ({ updateMutate: vi.fn() }));
vi.mock('@/hooks/useItemActions', () => ({ useUpdateItem: () => ({ mutate: updateMutate }) }));
// 카테고리 편집기는 이 테스트 관심사가 아니라 막는다
vi.mock('@/hooks/useCategories', () => ({ useCategories: () => ({ data: [] }) }));

const make = (over: Partial<ItemDetail> = {}): ItemDetail => ({
  itemId: 5,
  type: 'MEMO',
  status: 'DONE',
  title: '원래 제목',
  url: null,
  content: '원래 본문',
  summary: null,
  imageUrl: null,
  preview: { thumbnailUrl: null, description: null },
  categories: [],
  favorite: false,
  createdAt: new Date().toISOString(),
  deletedAt: null,
  ...over,
});

const titleInput = (c: HTMLElement) =>
  c.querySelector('input[aria-label="제목"]') as HTMLInputElement;
const bodyArea = (c: HTMLElement) =>
  c.querySelector('textarea[aria-label="본문"]') as HTMLTextAreaElement;
const saveBtn = (c: HTMLElement) =>
  [...c.querySelectorAll('button')].find((b) => /저장/.test(b.textContent ?? ''));

beforeEach(() => updateMutate.mockReset());

describe('MemoBody 편집', () => {
  it('제목·본문을 기존 값으로 채우고, 바뀌기 전엔 저장 버튼이 없다', async () => {
    const { container } = await render(<MemoBody item={make()} workspaceId={3} />);
    expect(titleInput(container).value).toBe('원래 제목');
    expect(bodyArea(container).value).toBe('원래 본문');
    expect(saveBtn(container)).toBeUndefined();
  });

  it('내용을 바꾸면 저장 버튼이 뜨고, 누르면 title·content 를 저장한다', async () => {
    const { container } = await render(<MemoBody item={make()} workspaceId={3} />);
    await userEvent.fill(bodyArea(container), '고친 본문');

    const btn = saveBtn(container)!;
    expect(btn).toBeTruthy();
    await userEvent.click(btn);
    expect(updateMutate).toHaveBeenCalledWith({ title: '원래 제목', content: '고친 본문' });
  });
});
