import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import EmptyState from '@/components/domain/library/EmptyState';

describe('EmptyState', () => {
  it('필터가 없으면 첫 저장 안내를 보여준다', async () => {
    const { container } = await render(<EmptyState />);
    expect(container.textContent).toContain('아직 저장한 게 없어요');
    expect(container.textContent).toContain('우주인이 알아서 분류');
  });

  it('필터가 걸려 있으면 결과 없음 안내를 보여준다', async () => {
    const { container } = await render(<EmptyState filtered />);
    expect(container.textContent).toContain('조건에 맞는 항목이 없어요');
    expect(container.textContent).toContain('필터를 바꾸거나');
  });

  it('안내 이미지는 장식이라 스크린리더에서 숨긴다', async () => {
    const { container } = await render(<EmptyState />);
    const img = container.querySelector('img');
    expect(img?.getAttribute('aria-hidden')).toBe('true');
    expect(img?.getAttribute('alt')).toBe('');
  });
});
