import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import ProcessingBadge from '@/components/domain/stage/ProcessingBadge';

describe('ProcessingBadge', () => {
  it('count 가 0 이면 아무것도 렌더하지 않는다', async () => {
    const { container } = await render(<ProcessingBadge count={0} label="별 만드는 중" />);
    expect(container.textContent).toBe('');
  });

  it('count·label 을 함께 보여준다', async () => {
    const { container } = await render(<ProcessingBadge count={3} label="별 만드는 중" />);
    expect(container.textContent).toContain('별 만드는 중');
    expect(container.textContent).toContain('3개');
  });

  it('로딩 스피너(role=status)를 포함한다', async () => {
    const { container } = await render(<ProcessingBadge count={1} label="장소 찾는 중" />);
    expect(container.querySelector('[role="status"]')).not.toBeNull();
  });
});
