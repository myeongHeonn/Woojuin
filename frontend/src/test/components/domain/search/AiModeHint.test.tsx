import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import AiModeHint from '@/components/domain/search/AiModeHint';

describe('AiModeHint', () => {
  it('AI 모드가 꺼져 있으면 권유 문구를 보여준다', async () => {
    const { container } = await render(<AiModeHint aiMode={false} />);
    expect(container.textContent).toContain('AI 모드로 바꿔보세요');
  });

  it('AI 모드가 켜져 있으면 아무것도 그리지 않는다', async () => {
    const { container } = await render(<AiModeHint aiMode />);
    expect(container.querySelector('p')).toBeNull();
  });
});
