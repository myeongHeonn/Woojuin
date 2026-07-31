import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import AiModeHint from '@/components/domain/search/AiModeHint';

describe('AiModeHint', () => {
  it('AI 모드 권유 문구를 보여준다', async () => {
    const { container } = await render(<AiModeHint />);
    expect(container.textContent).toContain('AI 모드로 바꿔보세요');
  });
});
