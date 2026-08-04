import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { IS_INSTALLED_APP } from '@/utils/installedApp';
import { useGoBack } from '@/hooks/useGoBack';

/**
 * 설치된 앱에서는 뷰 전환이 히스토리를 쌓지 않아, 뷰에서 뒤로가기가 앱을 닫는다(안드로이드) /
 * 아무 일도 하지 않는다(iOS). 브라우저 탭은 그대로 쌓여 뒤로가기가 정상 동작한다.
 */
describe('IS_INSTALLED_APP', () => {
  it('브라우저 탭에서는 false 다', () => {
    // 테스트는 브라우저 탭에서 돈다. true 로 잘못 판정하면 웹에서도 뷰 전환이 replace 가 되어
    // 뒤로가기로 이전 뷰에 돌아갈 수 없게 된다
    expect(IS_INSTALLED_APP).toBe(false);
  });
});

describe('useGoBack — 실제 히스토리를 되짚는다', () => {
  const Probe = ({ fallback }: { fallback: string }) => {
    const goBack = useGoBack(fallback);
    return (
      <button type="button" onClick={goBack}>
        뒤로
      </button>
    );
  };

  it('되돌아갈 뒤가 있으면 그 화면으로 간다', async () => {
    // 가입 → 개인정보처리방침으로 들어온 경우. 앱 전체 히스토리를 한 항목으로 고정하면
    // 이 복귀가 불가능해진다 — 그래서 뷰 전환만 replace 로 둔다
    const { container } = await render(
      <MemoryRouter initialEntries={['/signup', '/privacy']} initialIndex={1}>
        <Routes>
          <Route path="/signup" element={<div>가입 화면</div>} />
          <Route path="/privacy" element={<Probe fallback="/" />} />
          <Route path="/" element={<div>랜딩</div>} />
        </Routes>
      </MemoryRouter>,
    );

    container.querySelector('button')!.click();

    await vi.waitFor(() => {
      expect(container.textContent).toContain('가입 화면');
    });
  });

  it('되돌아갈 뒤가 없으면 fallback 으로 간다', async () => {
    const { container } = await render(
      <MemoryRouter initialEntries={['/privacy']}>
        <Routes>
          <Route path="/privacy" element={<Probe fallback="/" />} />
          <Route path="/" element={<div>랜딩</div>} />
        </Routes>
      </MemoryRouter>,
    );

    container.querySelector('button')!.click();

    await vi.waitFor(() => {
      expect(container.textContent).toContain('랜딩');
    });
  });
});
