import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import ErrorCodeMark from '@/components/domain/error/ErrorCodeMark';

/**
 * 이 컴포넌트의 핵심은 "링이 숫자 0 처럼 보이는가"이고, 그건 **크기와 밑선**이 결정한다.
 * 실제 Chromium 에서 돌므로 폰트 메트릭과 레이아웃을 직접 재서 검증한다.
 */

/** 글자 `4` 의 잉크 높이 — 링이 이 높이와 같아야 숫자 사이에서 튀지 않는다 */
const digitInkHeight = (element: Element): number => {
  const style = getComputedStyle(element);
  const context = document.createElement('canvas').getContext('2d')!;
  context.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
  const metrics = context.measureText('4');
  return metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
};

/** 인라인 요소들이 앉는 베이스라인 y 좌표 */
const baselineY = (container: Element): number => {
  const probe = document.createElement('span');
  probe.style.cssText = 'display:inline-block;width:0;height:0';
  container.appendChild(probe);
  const y = probe.getBoundingClientRect().bottom;
  probe.remove();
  return y;
};

describe('ErrorCodeMark', () => {
  it('0 자리는 글자가 아니라 로고 링으로 그린다', async () => {
    const { container } = await render(<ErrorCodeMark code="404" />);

    // 화면에 남는 글자는 4 두 개뿐 — 0 은 SVG 가 대신한다
    expect(container.textContent).toBe('44');
    expect(container.querySelectorAll('svg')).toHaveLength(1);
  });

  it('링이 숫자를 대신하므로 코드를 읽어 주는 라벨을 둔다', async () => {
    const { container } = await render(<ErrorCodeMark code="404" />);

    const mark = container.querySelector('[role="img"]');
    // 라벨이 없으면 스크린리더가 "4 4" 로 읽어 코드가 404 인지 알 수 없다
    expect(mark?.getAttribute('aria-label')).toBe('오류 코드 404');
  });

  it('링 높이가 숫자 잉크 높이와 맞고, 밑선이 베이스라인에 앉는다', async () => {
    const { container } = await render(<ErrorCodeMark code="404" />);

    const mark = container.querySelector('[role="img"]')!;
    const ring = mark.querySelector(':scope > span[aria-hidden]')!.getBoundingClientRect();

    // 1px 여유: em 계산과 폰트 메트릭 사이의 반올림
    expect(Math.abs(ring.height - digitInkHeight(mark))).toBeLessThan(1);
    expect(Math.abs(ring.bottom - baselineY(mark))).toBeLessThan(1);
  });

  it('링 지름은 글자 크기를 따라간다 — 좁은 화면에서 숫자만 작아지면 안 된다', async () => {
    const { container } = await render(
      <div style={{ fontSize: 40 }}>
        <ErrorCodeMark code="404" />
      </div>,
    );

    const mark = container.querySelector('[role="img"]') as HTMLElement;
    // ErrorCodeMark 자신이 clamp() 로 크기를 정하므로 부모 폰트에 끌려가지 않는다.
    // 검증할 것은 "링이 그 시점의 글자 크기에 비례한다"는 관계다.
    const fontSize = parseFloat(getComputedStyle(mark).fontSize);
    const ring = mark.querySelector(':scope > span[aria-hidden]')!.getBoundingClientRect();

    expect(ring.height / fontSize).toBeCloseTo(0.71, 2);
  });

  it('상태 코드가 없는 오류면 링만 보여 준다', async () => {
    const { container } = await render(<ErrorCodeMark />);

    expect(container.textContent).toBe('');
    expect(container.querySelectorAll('svg')).toHaveLength(1);
    expect(container.querySelector('[role="img"]')?.getAttribute('aria-label')).toBe('오류');
  });

  it('0 이 두 개면 링도 두 개다', async () => {
    const { container } = await render(<ErrorCodeMark code="500" />);

    expect(container.textContent).toBe('5');
    expect(container.querySelectorAll('svg')).toHaveLength(2);
  });
});
