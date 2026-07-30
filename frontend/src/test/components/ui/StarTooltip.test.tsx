import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import StarTooltip from '@/components/ui/StarTooltip';
import { INFO_CARD_CLASS } from '@/components/ui/infoCardStyles';
import type { StarNode } from '@/utils/scene';

const node: StarNode = {
  star: {
    id: 1,
    position: [0, 0, 0],
    title: '서울 여행',
    type: 'MEMO',
  },
  isHub: false,
  categoryName: '여행',
  cssColor: '#8fb4ff',
};

const renderTooltip = (x: number, y: number) =>
  render(
    <div className="relative h-[300px] w-[400px]">
      <StarTooltip
        node={node}
        position={{ x, y, visible: true }}
        onClick={vi.fn()}
        onPointerOverChange={vi.fn()}
      />
    </div>,
  );

describe('StarTooltip 위치 보정', () => {
  it('위쪽 공간이 부족하면 별 아래에 표시한다', async () => {
    const { container } = await renderTooltip(200, 20);
    const tooltip = container.querySelector('[data-testid="star-info-card"]') as HTMLElement;

    await expect.poll(() => tooltip.dataset.placement).toBe('bottom');
    expect(tooltip.classList.contains(INFO_CARD_CLASS.root)).toBe(true);
    expect(Number.parseFloat(tooltip.style.top)).toBeGreaterThan(20);
  });

  it('아래쪽 공간이 부족하면 별 위에 표시한다', async () => {
    const { container } = await renderTooltip(200, 280);
    const tooltip = container.querySelector('[data-testid="star-info-card"]') as HTMLElement;

    await expect.poll(() => tooltip.dataset.placement).toBe('top');
    expect(Number.parseFloat(tooltip.style.top)).toBeLessThan(280);
  });

  it('좌우 가장자리에서도 카드 전체를 화면 안에 유지한다', async () => {
    const leftRender = await renderTooltip(2, 150);
    const leftTooltip = leftRender.container.querySelector(
      '[data-testid="star-info-card"]',
    ) as HTMLElement;
    await expect.poll(() => leftTooltip.style.visibility).toBe('visible');
    expect(Number.parseFloat(leftTooltip.style.left)).toBeGreaterThanOrEqual(12);
    await leftRender.unmount();

    const rightRender = await renderTooltip(398, 150);
    const rightHost = rightRender.container.firstElementChild as HTMLElement;
    const rightTooltip = rightRender.container.querySelector(
      '[data-testid="star-info-card"]',
    ) as HTMLElement;
    await expect.poll(() => rightTooltip.style.visibility).toBe('visible');
    expect(
      Number.parseFloat(rightTooltip.style.left) + rightTooltip.offsetWidth,
    ).toBeLessThanOrEqual(rightHost.clientWidth - 12);
  });
});
