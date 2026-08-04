import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import NotFoundPage from '@/pages/NotFoundPage';

/**
 * `*` 라우트로 들어오는 화면이라, 라우터에 얹어서 확인한다 —
 * 링크·뒤로가기가 라우터 없이는 동작 자체가 성립하지 않는다.
 */
const renderAt = async (path: string) => {
  const router = createMemoryRouter(
    [
      { path: '/', element: <div>랜딩</div> },
      { path: '*', element: <NotFoundPage /> },
    ],
    { initialEntries: ['/'] },
  );
  const result = await render(<RouterProvider router={router} />);
  // 처음부터 없는 주소로 시작하지 않고 이동해서 온다 — 그래야 히스토리가 생겨
  // "뒤로 가기"가 의미를 갖는다(첫 진입이면 돌아갈 곳이 앱 밖이다)
  await router.navigate(path);
  // navigate 의 프라미스는 라우터 상태까지만 보장한다 — React 가 새 화면을 그리는 건
  // 그다음 렌더라 여기서 기다려야 한다
  await vi.waitFor(() => {
    expect(result.container.textContent).toContain('궤도를 벗어났어요');
  });
  return { ...result, router };
};

describe('NotFoundPage', () => {
  it('없는 주소로 이동하면 404 안내가 나온다', async () => {
    const { container } = await renderAt('/이런-주소는-없다');

    expect(container.textContent).toContain('궤도를 벗어났어요');
    expect(container.querySelector('[role="img"]')?.getAttribute('aria-label')).toBe(
      '오류 코드 404',
    );
  });

  it('홈으로 돌아가는 링크를 준다', async () => {
    const { container } = await renderAt('/없는곳');

    const home = [...container.querySelectorAll('a')].find(
      (anchor) => anchor.textContent === '홈으로 돌아가기',
    );
    // /home 이 아니라 / 로 보낸다 — 로그인 여부를 모르는 화면이라, 랜딩에 맡기면
    // 로그인 상태면 AuthLayout 이 알아서 앱으로 넘긴다
    expect(home?.getAttribute('href')).toBe('/');
  });

  it('뒤로가기를 누르면 홈으로 보낸다', async () => {
    // 히스토리를 되짚지 않는다 — 앱이 히스토리 항목을 늘리지 않아 뒤가 비어 있고,
    // navigate(-1) 은 앱을 벗어나 버린다 (utils/singleHistoryEntry, hooks/useGoBack)
    const { container, router } = await renderAt('/없는곳');

    const back = [...container.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '뒤로 가기',
    );
    expect(back).toBeTruthy();

    back!.click();
    await vi.waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
  });

  it('첫 진입(공유 링크·북마크)이라도 뒤로가기가 막히지 않는다', async () => {
    // 이동 없이 곧바로 없는 주소에서 시작 — 되짚을 뒤가 아예 없는 상태.
    // 목적지를 직접 지정하므로 이 경우에도 사용자가 빠져나갈 수 있다.
    const router = createMemoryRouter(
      [
        { path: '/', element: <div>랜딩</div> },
        { path: '*', element: <NotFoundPage /> },
      ],
      { initialEntries: ['/없는곳'] },
    );
    const { container } = await render(<RouterProvider router={router} />);

    const back = [...container.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '뒤로 가기',
    );
    expect(back).toBeTruthy();

    back!.click();
    await vi.waitFor(() => {
      expect(router.state.location.pathname).toBe('/');
    });
  });

  it('뒤로 가는 버튼은 하나뿐이고, 홈 버튼 오른쪽에 온다', async () => {
    const { container } = await renderAt('/없는곳');

    // 뒤로 가는 수단이 둘이면 어느 걸 눌러야 할지 모호해진다
    const backish = [...container.querySelectorAll('button, a')].filter((element) =>
      /뒤로|이전/.test(element.textContent + (element.getAttribute('aria-label') ?? '')),
    );
    expect(backish).toHaveLength(1);

    // 순서: 주 동작(홈) → 뒤로 가기. DOM 순서가 곧 화면 좌우 순서다(flex row)
    const row = backish[0].parentElement!;
    const labels = [...row.children].map((child) => child.textContent?.trim());
    expect(labels).toEqual(['홈으로 돌아가기', '뒤로 가기']);
  });
});
