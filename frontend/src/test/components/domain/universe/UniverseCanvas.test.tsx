import { flushSync } from 'react-dom';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import UniverseCanvas from '@/components/domain/universe/UniverseCanvas';
import type { SceneCallbacks, StarNode, UniverseScene } from '@/utils/scene';
import type { Star, UniverseResponse } from '@/types/universe';

let sceneCallbacks: SceneCallbacks | undefined;

vi.mock('@/utils/scene', () => ({
  createUniverseScene: (_canvas: HTMLCanvasElement, callbacks: SceneCallbacks): UniverseScene => {
    sceneCallbacks = callbacks;
    return {
      focusOn: vi.fn(),
      setPointerOverTooltip: vi.fn(),
      dispose: vi.fn(),
    };
  },
}));

const star: Star = {
  id: 42,
  position: [1, 2, 3],
  title: '서울 여행',
  type: 'URL',
  url: 'https://example.com',
};

const starNode: StarNode = {
  star,
  isHub: false,
  categoryName: '여행',
  cssColor: '#8fb4ff',
};

const universe: UniverseResponse = {
  constellations: [
    {
      categoryId: 1,
      categoryName: '여행',
      color: 0x8fb4ff,
      items: [star],
    },
  ],
  unclassified: [],
};

describe('UniverseCanvas 아이템 선택', () => {
  it('별 클릭은 정보 카드만 표시하고, 정보 카드 클릭이 상세 모달 요청으로 이어진다', async () => {
    const onOpenItem = vi.fn();
    const { container } = await render(<UniverseCanvas data={universe} onOpenItem={onOpenItem} />);
    await expect.poll(() => sceneCallbacks).toBeDefined();

    flushSync(() => {
      sceneCallbacks?.onSelect(starNode, { x: 120, y: 80, visible: true });
    });

    const tooltip = container.querySelector('[data-testid="star-info-card"]') as HTMLElement;
    expect(tooltip).not.toBeNull();
    expect(tooltip.textContent).toContain('서울 여행');
    expect(tooltip.textContent).toContain('클릭하여 상세 보기');
    expect(onOpenItem).not.toHaveBeenCalled();

    await userEvent.click(tooltip);
    expect(onOpenItem).toHaveBeenCalledWith(42);
  });
});
