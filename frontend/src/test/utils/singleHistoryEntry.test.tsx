import { describe, it, expect, vi, afterEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { keepSingleHistoryEntry } from '@/utils/singleHistoryEntry';
import { useGoBack } from '@/hooks/useGoBack';

/**
 * 설치된 앱에서 뒤로가기는 앱 안을 걷지 않고 곧바로 앱을 벗어나야 한다 — 그러려면 히스토리
 * 항목이 늘지 않아야 한다. 라우터에 "항상 replace" 스위치가 없어 pushState 를 갈아 끼운다.
 */
const originalPushState = window.history.pushState;

afterEach(() => {
  window.history.pushState = originalPushState;
  vi.restoreAllMocks();
});

describe('keepSingleHistoryEntry', () => {
  it('pushState 를 replaceState 로 돌린다 — 항목이 늘지 않는다', () => {
    const replaceSpy = vi.spyOn(window.history, 'replaceState');

    keepSingleHistoryEntry();
    // 같은 URL 로 부른다 — 이 테스트가 확인할 건 위임 여부이고, 주소를 바꾸면 러너가 흔들린다
    window.history.pushState({ probe: 1 }, '', window.location.href);

    expect(replaceSpy).toHaveBeenCalledTimes(1);
    expect(replaceSpy.mock.calls[0][0]).toEqual({ probe: 1 });
  });

  it('두 번 걸어도 안전하다', () => {
    keepSingleHistoryEntry();
    keepSingleHistoryEntry();
    const replaceSpy = vi.spyOn(window.history, 'replaceState');

    window.history.pushState(null, '', window.location.href);

    // 재귀로 두 번 불리거나 스택이 터지지 않는다
    expect(replaceSpy).toHaveBeenCalledTimes(1);
  });
});

describe('useGoBack — 히스토리를 되짚지 않는다', () => {
  const Probe = ({ to }: { to: string }) => {
    const goBack = useGoBack(to);
    return (
      <button type="button" onClick={goBack}>
        뒤로
      </button>
    );
  };

  it('지정한 경로로 이동한다', async () => {
    // navigate(-1) 이면 뒤가 비어 있어 앱을 벗어난다 — 화면 안의 버튼이 앱을 닫으면 안 된다
    const { container } = await render(
      <MemoryRouter initialEntries={['/privacy']}>
        <Routes>
          <Route path="/privacy" element={<Probe to="/" />} />
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
