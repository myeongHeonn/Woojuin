import { flushSync } from 'react-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import UniverseCanvas from '@/components/domain/universe/UniverseCanvas';
import type { CameraState, SceneCallbacks, StarNode, UniverseScene } from '@/utils/scene';
import type { Star, UniverseResponse } from '@/types/universe';

let sceneCallbacks: SceneCallbacks | undefined;
/** createUniverseScene 이 실제로 무엇을 받아 호출됐는지 — 카메라 이어받기 검증용 */
const createSceneCalls: Array<{
  data: UniverseResponse;
  initialCamera: CameraState | undefined;
}> = [];

vi.mock('@/utils/scene', () => ({
  createUniverseScene: (
    _canvas: HTMLCanvasElement,
    callbacks: SceneCallbacks,
    data: UniverseResponse,
    initialCamera: CameraState | undefined,
  ): UniverseScene => {
    sceneCallbacks = callbacks;
    createSceneCalls.push({ data, initialCamera });
    return {
      focusOn: vi.fn(),
      setHighlightedItems: vi.fn(),
      setActiveCategory: vi.fn(),
      setPointerOverTooltip: vi.fn(),
      getCameraState: vi.fn().mockReturnValue({ rotX: 0.42, rotY: -1.1, camZ: 77 }),
      dispose: vi.fn(),
    };
  },
}));

beforeEach(() => {
  createSceneCalls.length = 0;
});

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
  categoryNames: ['여행'],
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

describe('UniverseCanvas 씬 재생성 — 카메라 시점 이어받기', () => {
  it('데이터가 바뀌어 씬을 다시 만들 때 직전 씬의 카메라 시점을 넘겨준다', async () => {
    const { rerender } = await render(<UniverseCanvas data={universe} />);
    await expect.poll(() => createSceneCalls.length).toBe(1);
    // 첫 씬은 이전 시점이 없다 — 기본값으로 시작
    expect(createSceneCalls[0].initialCamera).toBeUndefined();

    const nextUniverse: UniverseResponse = {
      ...universe,
      unclassified: [{ ...star, id: 99, title: '새 별' }],
    };
    await rerender(<UniverseCanvas data={nextUniverse} />);

    await expect.poll(() => createSceneCalls.length).toBe(2);
    // 직전 씬의 getCameraState() 반환값이 다음 씬 생성 인자로 그대로 전달돼야 한다
    expect(createSceneCalls[1].initialCamera).toEqual({ rotX: 0.42, rotY: -1.1, camZ: 77 });
  });
});
