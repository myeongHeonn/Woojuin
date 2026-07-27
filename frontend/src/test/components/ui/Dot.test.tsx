import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import Dot from '@/components/ui/Dot';

const dot = (c: HTMLElement) => c.querySelector('span')!;

describe('Dot', () => {
  it('hex 를 주면 그 색으로 칠한다 (bg-current ← style color)', async () => {
    // 서버가 준 카테고리 hex(#C9B8FF)를 그대로 렌더해야 한다
    const { container } = await render(<Dot hex="#C9B8FF" />);
    // #C9B8FF = rgb(201, 184, 255)
    expect(getComputedStyle(dot(container)).backgroundColor).toBe('rgb(201, 184, 255)');
  });

  it('hex 가 없으면 토큰 색(accent) 클래스를 쓴다', async () => {
    const { container } = await render(<Dot color="accent" />);
    expect(dot(container).className).toContain('text-accent');
    expect(dot(container).getAttribute('style')).toBeNull();
  });
});
