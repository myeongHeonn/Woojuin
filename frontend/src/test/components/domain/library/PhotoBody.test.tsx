import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import PhotoBody from '@/components/domain/library/detail/PhotoBody';
import { downloadImage } from '@/utils/downloadImage';
import type { ItemDetail } from '@/types/item';

// downloadImage(부수효과)만 막고 imageFilename(순수)은 실제를 쓴다
vi.mock('@/utils/downloadImage', async (orig) => ({
  ...(await orig<typeof import('@/utils/downloadImage')>()),
  downloadImage: vi.fn(),
}));
const mockDownload = vi.mocked(downloadImage);

// 카테고리 편집기(useCategories·useUpdateItem)는 이 테스트의 관심사가 아니라 막는다
vi.mock('@/hooks/useCategories', () => ({ useCategories: () => ({ data: [] }) }));
vi.mock('@/hooks/useItemActions', () => ({
  useUpdateItem: () => ({ mutate: vi.fn() }),
  useDeleteItem: () => ({ mutate: vi.fn() }),
}));

const make = (over: Partial<ItemDetail> = {}): ItemDetail => ({
  itemId: 7,
  type: 'IMAGE',
  status: 'DONE',
  title: '영수증',
  url: null,
  content: null,
  summary: null,
  imageUrl: 'https://s3/x/photo.jpg?sig=a',
  preview: { thumbnailUrl: null, description: null },
  categories: [],
  favorite: false,
  createdAt: new Date().toISOString(),
  deletedAt: null,
  ...over,
});

const dlBtn = (c: HTMLElement) => c.querySelector('button[aria-label="원본 다운로드"]');

beforeEach(() => mockDownload.mockReset());

describe('PhotoBody 다운로드', () => {
  it('원본이 있으면 다운로드 버튼을 보여주고, 클릭하면 URL·파일명으로 내려받는다', async () => {
    const { container } = await render(<PhotoBody item={make()} workspaceId={3} />);
    expect(dlBtn(container)).not.toBeNull();

    await userEvent.click(dlBtn(container)!);
    expect(mockDownload).toHaveBeenCalledWith('https://s3/x/photo.jpg?sig=a', '영수증.jpg');
  });

  it('원본(imageUrl)이 없으면 다운로드 버튼을 감춘다', async () => {
    const { container } = await render(
      <PhotoBody item={make({ imageUrl: null })} workspaceId={3} />,
    );
    expect(dlBtn(container)).toBeNull();
  });
});
