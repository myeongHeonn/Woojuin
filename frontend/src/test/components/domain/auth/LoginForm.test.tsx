import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider, createStore } from 'jotai';
import LoginForm from '@/components/domain/auth/LoginForm';
import { login } from '@/services/auth';

vi.mock('@/services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/auth')>()),
  login: vi.fn(),
}));

/** axios.isAxiosError 는 isAxiosError 플래그만 보므로 실제 요청 없이 서버 응답을 흉내 낼 수 있다. */
const apiError = (status: number, message: string) =>
  Object.assign(new Error(message), {
    isAxiosError: true,
    response: { status, data: { status, message, data: null } },
  });

/**
 * 테스트마다 **새 jotai store** 를 쓴다.
 * 토큰 atom 들은 getOnInit: true 라 store 가 만들어질 때 한 번 스토리지를 읽는데,
 * 기본 store 를 공유하면 앞 테스트가 메모리에 남긴 값이 그대로 보여서
 * "이 테스트가 심어둔 스토리지 값"이 반영되지 않는다.
 */
const renderLoginForm = () =>
  render(
    <JotaiProvider store={createStore()}>
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginForm />} />
            {/* 로그인 후 도착지 — 어느 쪽으로 갔는지를 이 문구들로 구분한다 */}
            <Route path="/home" element={<p>개인 워크스페이스</p>} />
            <Route path="/workspace/:workspaceId/library" element={<p>서재 화면</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </JotaiProvider>,
  );

const emailInput = (c: HTMLElement) => c.querySelector('input[type="email"]') as HTMLInputElement;
const passwordInput = (c: HTMLElement) =>
  c.querySelector('input[type="password"]') as HTMLInputElement;
const submitButton = (c: HTMLElement) => c.querySelector('button[type="submit"]') as HTMLElement;

const fillAndSubmit = async (container: HTMLElement, password = 'password123') => {
  await userEvent.type(emailInput(container), 'astronaut@woojuin.com');
  await userEvent.type(passwordInput(container), password);
  await userEvent.click(submitButton(container));
};

beforeEach(() => {
  vi.mocked(login).mockReset();
  localStorage.clear();
  sessionStorage.clear();
});

describe('로그인 폼', () => {
  // 구글 Cloud 프로젝트 정지 기간 동안 감춘다(LoginForm.GOOGLE_LOGIN_ENABLED).
  // 버튼이 살아 있으면 누른 사람이 우리 화면 밖 구글 오류 페이지로 떨어져 돌아올 방법이 없다.
  it('구글 로그인 버튼을 노출하지 않는다', async () => {
    const { container } = await renderLoginForm();

    expect(container.textContent).not.toContain('Google');
    expect(container.querySelector('a[href*="oauth2/authorization/google"]')).toBeNull();
  });

  it('이메일 가입 경로를 안내한다', async () => {
    const { container } = await renderLoginForm();

    expect(container.querySelector('a[href="/signup"]')?.textContent).toBe('회원가입');
  });

  it('이메일·비밀번호로 로그인하면 토큰을 저장하고 기본 도착지로 이동한다', async () => {
    vi.mocked(login).mockResolvedValue({ accessToken: 'access-1', refreshToken: 'refresh-1' });
    const { container } = await renderLoginForm();

    await fillAndSubmit(container);

    await expect.poll(() => container.textContent).toContain('개인 워크스페이스');
    expect(vi.mocked(login).mock.calls[0][0]).toEqual({
      email: 'astronaut@woojuin.com',
      password: 'password123',
    });
    // 이후 요청이 붙일 토큰 — 구글 로그인(OAuthCallbackPage)과 같은 키에 저장돼야 한다
    expect(JSON.parse(localStorage.getItem('woojuin:accessToken') ?? 'null')).toBe('access-1');
    expect(JSON.parse(localStorage.getItem('woojuin:refreshToken') ?? 'null')).toBe('refresh-1');
  });

  it('로그인 전 가려던 경로가 있으면 그리로 돌려보낸다', async () => {
    // AuthLayout 이 가드에 걸린 경로를 여기에 남겨둔다
    sessionStorage.setItem('woojuin:postLoginRedirect', JSON.stringify('/workspace/9/library'));
    vi.mocked(login).mockResolvedValue({ accessToken: 'access-1', refreshToken: 'refresh-1' });
    const { container } = await renderLoginForm();

    await fillAndSubmit(container);

    await expect.poll(() => container.textContent).toContain('서재 화면');
    // 한 번 쓰고 비워야 다음 로그인이 옛 목적지로 끌려가지 않는다
    expect(sessionStorage.getItem('woojuin:postLoginRedirect')).toBe('null');
  });

  it('자격 증명이 틀리면 서버가 준 문구를 그대로 보여준다', async () => {
    vi.mocked(login).mockRejectedValue(apiError(401, '이메일 또는 비밀번호가 올바르지 않습니다'));
    const { container } = await renderLoginForm();

    await fillAndSubmit(container, 'wrong-password');

    await expect
      .poll(() => container.textContent)
      .toContain('이메일 또는 비밀번호가 올바르지 않습니다');
    expect(localStorage.getItem('woojuin:accessToken')).toBeNull();
  });

  // 이 경우엔 zod 메시지가 뜨지 않는다 — input[type=email] 의 브라우저 기본 검증이
  // 제출 자체를 먼저 막아 handleSubmit 이 돌지 않고, 안내는 브라우저 툴팁이 대신한다.
  // 어느 쪽이 막든 지켜야 할 건 "요청이 나가지 않는다" 이므로 그것만 확인한다.
  it('이메일 형식이 아니면 요청을 보내지 않는다', async () => {
    const { container } = await renderLoginForm();

    await userEvent.type(emailInput(container), 'not-an-email');
    await userEvent.type(passwordInput(container), 'password123');
    await userEvent.click(submitButton(container));

    expect(login).not.toHaveBeenCalled();
    expect(container.textContent).toContain('회원가입'); // 로그인 화면에 그대로 남아 있다
  });

  it('비밀번호가 비어 있으면 검증 메시지를 띄우고 요청을 보내지 않는다', async () => {
    const { container } = await renderLoginForm();

    await userEvent.type(emailInput(container), 'astronaut@woojuin.com');
    await userEvent.click(submitButton(container));

    await expect.poll(() => container.textContent).toContain('비밀번호는 필수입니다');
    expect(login).not.toHaveBeenCalled();
  });
});
