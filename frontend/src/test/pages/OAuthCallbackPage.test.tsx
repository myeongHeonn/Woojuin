import { describe, it, expect, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider as JotaiProvider, createStore } from 'jotai';
import OAuthCallbackPage from '@/pages/OAuthCallbackPage';
import { accessTokenAtom, postLoginRedirectAtom } from '@/stores/authAtoms';

const renderCallback = (search: string, store = createStore()) =>
  render(
    <JotaiProvider store={store}>
      <MemoryRouter initialEntries={[`/oauth/callback${search}`]}>
        <Routes>
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/oauth/onboarding" element={<p>온보딩 화면</p>} />
          <Route path="/home" element={<p>개인 워크스페이스</p>} />
          <Route path="/workspace/9/library" element={<p>서재 화면</p>} />
          <Route path="/login" element={<p>로그인 화면</p>} />
        </Routes>
      </MemoryRouter>
    </JotaiProvider>,
  );

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe('OAuthCallbackPage', () => {
  it('기존 가입자(isNewUser=false)면 온보딩을 건너뛰고 기본 도착지로 이동한다', async () => {
    const { container } = await renderCallback(
      '?accessToken=access-1&refreshToken=refresh-1&isNewUser=false',
    );

    await expect.poll(() => container.textContent).toContain('개인 워크스페이스');
  });

  it('신규 가입자(isNewUser=true)면 목적지 대신 온보딩 화면으로 보낸다', async () => {
    const { container } = await renderCallback(
      '?accessToken=access-1&refreshToken=refresh-1&isNewUser=true',
    );

    await expect.poll(() => container.textContent).toContain('온보딩 화면');
  });

  it('신규 가입자를 온보딩으로 보낼 때도 postLoginRedirect는 지우지 않는다', async () => {
    const store = createStore();
    store.set(postLoginRedirectAtom, '/workspace/9/library');

    await renderCallback('?accessToken=access-1&refreshToken=refresh-1&isNewUser=true', store);

    expect(store.get(postLoginRedirectAtom)).toBe('/workspace/9/library');
  });

  it('신규 가입자가 아니고 가려던 경로가 있으면 그리로 보낸다', async () => {
    const store = createStore();
    store.set(postLoginRedirectAtom, '/workspace/9/library');

    const { container } = await renderCallback(
      '?accessToken=access-1&refreshToken=refresh-1&isNewUser=false',
      store,
    );

    await expect.poll(() => container.textContent).toContain('서재 화면');
    expect(store.get(postLoginRedirectAtom)).toBeNull();
  });

  it('토큰이 없으면 로그인 화면으로 보낸다', async () => {
    const { container } = await renderCallback('');

    await expect.poll(() => container.textContent).toContain('로그인 화면');
  });

  it('신규 가입자면 토큰을 저장한다', async () => {
    const store = createStore();

    await renderCallback('?accessToken=access-1&refreshToken=refresh-1&isNewUser=true', store);

    expect(store.get(accessTokenAtom)).toBe('access-1');
  });
});
