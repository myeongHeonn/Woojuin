import { describe, it, expect, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { Provider as JotaiProvider, createStore } from 'jotai';
import OAuthCallbackPage from '@/pages/OAuthCallbackPage';
import { accessTokenAtom, postLoginRedirectAtom } from '@/stores/authAtoms';

/** 로그인 화면으로 넘어간 쿼리까지 보이게 한다 — 실패 사유가 실려 가는지 확인해야 한다. */
const LoginProbe = () => <p>로그인 화면{useLocation().search}</p>;

const renderCallback = (search: string, store = createStore()) =>
  render(
    <JotaiProvider store={store}>
      <MemoryRouter initialEntries={[`/oauth/callback${search}`]}>
        <Routes>
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/oauth/onboarding" element={<p>온보딩 화면</p>} />
          <Route path="/home" element={<p>개인 워크스페이스</p>} />
          <Route path="/workspace/9/library" element={<p>서재 화면</p>} />
          <Route path="/login" element={<LoginProbe />} />
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

  // 사유를 버리면 로그인 화면은 무슨 일이 있었는지 말할 수 없다 — 사용자에겐
  // "구글 버튼을 눌렀는데 아무 일도 없음"이 된다.
  it('실패 사유(error)를 로그인 화면까지 실어 보낸다', async () => {
    const { container } = await renderCallback('?error=withdrawn_user');

    await expect.poll(() => container.textContent).toContain('error=withdrawn_user');
  });

  it('사유가 없으면 쿼리를 붙이지 않는다', async () => {
    const { container } = await renderCallback('');

    await expect.poll(() => container.textContent).toContain('로그인 화면');
    expect(container.textContent).not.toContain('error=');
  });

  it('신규 가입자면 토큰을 저장한다', async () => {
    const store = createStore();

    await renderCallback('?accessToken=access-1&refreshToken=refresh-1&isNewUser=true', store);

    expect(store.get(accessTokenAtom)).toBe('access-1');
  });
});
