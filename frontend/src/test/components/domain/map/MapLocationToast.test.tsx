import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import MapLocationToast from '@/components/domain/map/MapLocationToast';

describe('MapLocationToast', () => {
  it('message 가 없으면 아무것도 렌더하지 않는다', async () => {
    const { container } = await render(<MapLocationToast message={null} />);
    expect(container.textContent).toBe('');
  });

  it('message 를 그대로 보여준다', async () => {
    const { container } = await render(
      <MapLocationToast message="위치 정보가 없어서 등록되지 않았어요" />,
    );
    expect(container.textContent).toContain('위치 정보가 없어서 등록되지 않았어요');
  });
});
