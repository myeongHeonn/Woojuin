import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider, createStore } from 'jotai';
import GuestOnly from '@/layouts/GuestOnly';
import { accessTokenAtom } from '@/stores/authAtoms';

/**
 * 이 가드가 막는 건 실제로 났던 증상이다 — 설치형 PWA 를 껐다 켜면 로그인이 유지되는데도
 * 랜딩 페이지가 떴다. PWA 는 cold start 마다 start_url(`/`)로 들어오고, `/` 는 AuthLayout
 * 밖이라 아무도 로그인 여부를 보지 않았다.
 */
const renderAt = (path: string, accessToken: string | null) => {
  const store = createStore();
  store.set(accessTokenAtom, accessToken);
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<GuestOnly />}>
            <Route path="/" element={<div>랜딩</div>} />
            <Route path="/login" element={<div>로그인 폼</div>} />
          </Route>
          <Route path="/home" element={<div>앱 홈</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
};

describe('GuestOnly', () => {
  it('로그인돼 있으면 / 에서 앱으로 보낸다 — PWA 를 껐다 켤 때 랜딩이 뜨던 문제', async () => {
    const { container } = await renderAt('/', 'token');
    expect(container.textContent).toContain('앱 홈');
    expect(container.textContent).not.toContain('랜딩');
  });

  it('로그인돼 있으면 /login 에서도 앱으로 보낸다', async () => {
    // 랜딩에서 "로그인"을 눌렀을 때 이미 로그인된 사람에게 폼을 내밀지 않는다
    const { container } = await renderAt('/login', 'token');
    expect(container.textContent).toContain('앱 홈');
  });

  it('로그인 안 돼 있으면 랜딩을 그대로 보여준다', async () => {
    const { container } = await renderAt('/', null);
    expect(container.textContent).toContain('랜딩');
  });

  it('로그인 안 돼 있으면 로그인 폼을 그대로 보여준다', async () => {
    const { container } = await renderAt('/login', null);
    expect(container.textContent).toContain('로그인 폼');
  });
});
