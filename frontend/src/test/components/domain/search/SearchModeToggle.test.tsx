import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import SearchModeToggle from '@/components/domain/search/SearchModeToggle';

describe('SearchModeToggle', () => {
  it('aiMode 를 aria-pressed 로 알리고, 클릭하면 토글 콜백을 부른다', async () => {
    const onToggle = vi.fn();
    const { container } = await render(<SearchModeToggle aiMode={false} onToggle={onToggle} />);
    const btn = container.querySelector('button')!;
    expect(btn.getAttribute('aria-pressed')).toBe('false');

    await userEvent.click(btn);
    expect(onToggle).toHaveBeenCalledOnce();
  });

  it('aiMode=true 면 aria-pressed=true', async () => {
    const { container } = await render(<SearchModeToggle aiMode onToggle={() => {}} />);
    expect(container.querySelector('button')?.getAttribute('aria-pressed')).toBe('true');
  });
});
