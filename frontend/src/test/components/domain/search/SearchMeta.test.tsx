import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import SearchMeta from '@/components/domain/search/SearchMeta';

describe('SearchMeta', () => {
  it('알릴 게 없으면 아무것도 그리지 않는다', async () => {
    const { container } = await render(<SearchMeta />);
    expect(container.textContent).toBe('');
  });

  it('AI 해석어·규칙기반 폴백·부분일치를 이어 보여준다', async () => {
    const { container } = await render(
      <SearchMeta interpretedQuery="을지로 파스타" aiPlanned={false} partialMatch />,
    );
    expect(container.textContent).toContain('을지로 파스타');
    expect(container.textContent).toContain('규칙 기반');
    expect(container.textContent).toContain('일부만 일치');
  });

  it('aiPlanned 가 true 면 규칙기반 문구는 없다', async () => {
    const { container } = await render(<SearchMeta interpretedQuery="x" aiPlanned />);
    expect(container.textContent).not.toContain('규칙 기반');
  });
});
