import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import ItemCard from '@/components/domain/library/ItemCard';
import type { Item, ItemStatus, ItemType } from '@/types/item';

/** 필요한 필드만 채운 아이템 — 나머지는 기본값 */
const make = (over: Partial<Item> & { type: ItemType; status: ItemStatus }): Item => ({
  itemId: 1,
  title: '제목',
  url: null,
  content: null,
  s3Key: null,
  imageUrl: null,
  preview: { thumbnailUrl: null, description: null },
  favorite: false,
  createdAt: '',
  deletedAt: null,
  ...over,
});

const card = (c: HTMLElement) => c.querySelector('button')!;

describe('ItemCard', () => {
  describe('제목·타입 라벨', () => {
    it('제목과 타입 라벨(링크/사진/메모)을 보여준다', async () => {
      const { container } = await render(
        <ItemCard item={make({ type: 'URL', status: 'DONE', title: '성수 카페' })} />,
      );
      expect(card(container).textContent).toContain('성수 카페');
      expect(card(container).textContent).toContain('링크');
    });

    it('제목이 없으면 "제목 없음" 으로 대체한다', async () => {
      const { container } = await render(
        <ItemCard item={make({ type: 'IMAGE', status: 'DONE', title: null })} />,
      );
      expect(card(container).textContent).toContain('제목 없음');
      expect(card(container).textContent).toContain('사진');
    });
  });

  describe('PROCESSING', () => {
    it('스피너와 "분석 중…" 을 보여주고 제목은 감춘다', async () => {
      const { container } = await render(
        <ItemCard item={make({ type: 'URL', status: 'PROCESSING', title: '아직 이름' })} />,
      );
      expect(container.querySelector('[role="status"]')).not.toBeNull(); // 스피너
      expect(card(container).textContent).toContain('분석 중…');
      // 분석 중엔 실제 제목을 쓰지 않는다
      expect(card(container).textContent).not.toContain('아직 이름');
    });
  });

  describe('PARTIAL·FAILED 는 DONE 과 똑같이 그린다', () => {
    it('스피너 없이 제목이 그대로 보인다', async () => {
      for (const status of ['PARTIAL', 'FAILED'] as const) {
        const { container } = await render(
          <ItemCard item={make({ type: 'URL', status, title: '실패해도 제목' })} />,
        );
        expect(container.querySelector('[role="status"]')).toBeNull(); // 스피너 없음
        expect(card(container).textContent).toContain('실패해도 제목');
      }
    });
  });

  describe('타입별 정사각 안쪽', () => {
    it('URL 은 링크 배지가 있다', async () => {
      const { container } = await render(<ItemCard item={make({ type: 'URL', status: 'DONE' })} />);
      // 배지 안 svg — 정사각 안에 아이콘이 있다
      expect(container.querySelector('svg')).not.toBeNull();
    });

    it('MEMO 는 회색 줄 스켈레톤(막대 4개)이다', async () => {
      const { container } = await render(
        <ItemCard item={make({ type: 'MEMO', status: 'DONE' })} />,
      );
      const bars = container.querySelectorAll('span.block.h-\\[3px\\]');
      expect(bars).toHaveLength(4);
    });

    it('사진(IMAGE)은 서버가 준 imageUrl 을 깐다', async () => {
      const { container } = await render(
        <ItemCard
          item={make({ type: 'IMAGE', status: 'DONE', imageUrl: 'https://s3/photo.png' })}
        />,
      );
      const thumb = container.querySelector('.absolute.inset-0') as HTMLElement;
      expect(thumb.style.backgroundImage).toContain('https://s3/photo.png');
    });

    it('링크(URL)는 preview.thumbnailUrl 을 쓴다 (imageUrl 아님)', async () => {
      const { container } = await render(
        <ItemCard
          item={make({
            type: 'URL',
            status: 'DONE',
            imageUrl: 'https://s3/should-not-use.png',
            preview: { thumbnailUrl: 'https://x/preview.png', description: null },
          })}
        />,
      );
      const thumb = container.querySelector('.absolute.inset-0') as HTMLElement;
      expect(thumb.style.backgroundImage).toContain('https://x/preview.png');
      expect(thumb.style.backgroundImage).not.toContain('should-not-use');
    });
  });
});
