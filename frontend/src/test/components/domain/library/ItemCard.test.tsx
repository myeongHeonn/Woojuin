import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import ItemCard from '@/components/domain/library/ItemCard';
import type { Item, ItemStatus, ItemType } from '@/types/item';

/** 필요한 필드만 채운 아이템 — 나머지는 기본값 */
const make = (over: Partial<Item> & { type: ItemType; status: ItemStatus }): Item => ({
  itemId: 1,
  title: '제목',
  url: null,
  summary: null,
  imageUrl: null,
  preview: { thumbnailUrl: null, description: null },
  categories: [],
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

  describe('즐겨찾기 표식', () => {
    it('즐겨찾기면 제목 옆에 노란 별을 보여준다', async () => {
      const { container } = await render(
        <ItemCard item={make({ type: 'URL', status: 'DONE', favorite: true })} />,
      );
      expect(container.querySelector('.text-star-yellow')).not.toBeNull();
    });

    it('즐겨찾기가 아니면 별이 없다', async () => {
      const { container } = await render(
        <ItemCard item={make({ type: 'URL', status: 'DONE', favorite: false })} />,
      );
      expect(container.querySelector('.text-star-yellow')).toBeNull();
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

    it('분석 중이면 비활성 — 클릭해도 열리지 않는다(모달 안 뜸)', async () => {
      const onClick = vi.fn();
      const { container } = await render(
        <ItemCard item={make({ type: 'URL', status: 'PROCESSING' })} onClick={onClick} />,
      );
      expect(card(container).disabled).toBe(true);
      // 비활성 버튼은 클릭 이벤트가 발생하지 않는다(userEvent 는 활성화를 기다리다 멈추므로 네이티브 클릭으로 확인)
      card(container).click();
      expect(onClick).not.toHaveBeenCalled();
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
      const thumb = container.querySelector('img') as HTMLImageElement;
      expect(thumb.src).toContain('https://s3/photo.png');
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
      const thumb = container.querySelector('img') as HTMLImageElement;
      expect(thumb.src).toContain('https://x/preview.png');
      expect(thumb.src).not.toContain('should-not-use');
    });

    // 카드 이미지가 화면 밖까지 전부 내려오면 대시보드 전송량이 폭발한다(실측 3MB) —
    // background-image 로는 지연 로딩이 불가능해 img 로 바꾼 것이 이 속성으로 드러난다.
    it('카드 이미지는 지연 로딩한다', async () => {
      const { container } = await render(
        <ItemCard
          item={make({ type: 'IMAGE', status: 'DONE', imageUrl: 'https://s3/photo.png' })}
        />,
      );
      const thumb = container.querySelector('img') as HTMLImageElement;
      expect(thumb.loading).toBe('lazy');
    });

    /**
     * img 는 대체 요소라 `absolute inset-0` 만으로는 늘어나지 않고 고유 크기로 그려진다
     * — 실제로 카드 아래가 빈 채 배포될 뻔했다. 정사각 상자를 꽉 채우는지 확인한다.
     * (상자는 88px 이지만 테두리 1px 씩이 있어 안쪽은 86px 이라, 숫자를 박지 않고
     *  부모 대비 비율로 본다)
     */
    it('카드 이미지가 정사각 상자를 꽉 채운다', async () => {
      const { container } = await render(
        <ItemCard
          item={make({ type: 'IMAGE', status: 'DONE', imageUrl: 'https://s3/photo.png' })}
        />,
      );
      const img = container.querySelector('img') as HTMLImageElement;
      const square = img.closest('.rounded-2xl') as HTMLElement;
      const box = img.getBoundingClientRect();
      const outer = square.getBoundingClientRect();

      // 세로가 덜 차면(고유 비율로 그려지면) 카드 아래가 빈다 — 이게 막으려는 회귀다
      expect(box.height).toBeGreaterThan(outer.height - 4);
      expect(box.width).toBeGreaterThan(outer.width - 4);
    });
  });
});
