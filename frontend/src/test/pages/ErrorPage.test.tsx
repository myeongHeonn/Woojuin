import { describe, it, expect, vi, afterEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import ErrorPage from '@/pages/ErrorPage';

/**
 * errorElement 자리의 화면이라, 실제로 에러를 던지는 라우트에 얹어서 확인한다.
 * (useRouteError 는 라우터가 에러를 들고 있을 때만 값을 준다)
 */
const renderThrowing = async (thrown: unknown) => {
  const router = createMemoryRouter([
    {
      path: '/',
      errorElement: <ErrorPage />,
      element: (() => {
        const Boom = () => {
          throw thrown;
        };
        return <Boom />;
      })(),
    },
  ]);
  return render(<RouterProvider router={router} />);
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ErrorPage', () => {
  it('렌더 중 터진 에러를 빈 화면 대신 안내로 바꾼다', async () => {
    // React 가 경계에서 잡힌 에러를 콘솔에 찍어 출력이 지저분해지는 것만 막는다
    vi.spyOn(console, 'error').mockImplementation(() => {});

    const { container } = await renderThrowing(new Error('뭔가 터졌다'));

    expect(container.textContent).toContain('잠시 교신이 끊겼어요');
    // 사용자가 빠져나갈 길이 있어야 한다
    expect(container.textContent).toContain('다시 시도');
    expect([...container.querySelectorAll('a')].some((a) => a.getAttribute('href') === '/')).toBe(
      true,
    );
  });

  it('에러를 삼키지 않고 콘솔에 남긴다', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await renderThrowing(new Error('원인을 알아야 한다'));

    // 커스텀 errorElement 를 두면 라우터가 대신 찍어 주지 않는다 — 우리가 찍어야 한다
    expect(spy.mock.calls.some((call) => String(call[0]).includes('[ErrorPage]'))).toBe(true);
  });

  it('배포로 청크가 갈린 경우는 고장이 아니라 새 버전 안내로 구분한다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    const { container } = await renderThrowing(
      new TypeError('Failed to fetch dynamically imported module: /assets/MapPage-abc123.js'),
    );

    expect(container.textContent).toContain('새 버전이 배포됐어요');
    expect(container.textContent).toContain('새로고침');
    // 저장물이 사라진 게 아니라는 걸 분명히 해 준다
    expect(container.textContent).toContain('저장한 내용은 그대로');
  });

  it('loader 가 던진 Response 는 그 상태 코드를 보여 준다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    const router = createMemoryRouter([
      {
        path: '/',
        errorElement: <ErrorPage />,
        loader: () => {
          throw new Response('nope', { status: 500, statusText: 'Server Error' });
        },
        element: <div>본문</div>,
      },
    ]);
    const { container } = await render(<RouterProvider router={router} />);
    // loader 는 비동기라 한 틱 기다린다
    await vi.waitFor(() => {
      expect(container.querySelector('[role="img"]')).toBeTruthy();
    });

    expect(container.querySelector('[role="img"]')?.getAttribute('aria-label')).toBe(
      '오류 코드 500',
    );
  });
});
