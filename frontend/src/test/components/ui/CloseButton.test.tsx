import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import CloseButton from '@/components/ui/CloseButton';

describe('CloseButton', () => {
  it('닫기 라벨을 가진 버튼을 그리고, 클릭하면 onClick 을 부른다', async () => {
    const onClick = vi.fn();
    const { container } = await render(<CloseButton onClick={onClick} />);

    const btn = container.querySelector('button[aria-label="닫기"]')!;
    expect(btn).not.toBeNull();

    await userEvent.click(btn);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('배치 className 을 덧붙일 수 있다', async () => {
    const { container } = await render(
      <CloseButton onClick={() => {}} className="absolute right-3.5 top-3.5" />,
    );
    expect(container.querySelector('button')?.className).toContain('absolute');
  });
});
