import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SignupForm from '@/components/domain/auth/SignupForm';
import { signup, checkEmailAvailability } from '@/services/auth';

vi.mock('@/services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/auth')>()),
  signup: vi.fn(),
  checkEmailAvailability: vi.fn(),
}));

const renderSignupForm = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={['/signup']}>
        <SignupForm />
      </MemoryRouter>
    </QueryClientProvider>,
  );

const submitButton = (c: HTMLElement) => c.querySelector('button[type="submit"]') as HTMLElement;
const privacyCheckbox = (c: HTMLElement) =>
  c.querySelector('input[type="checkbox"]') as HTMLInputElement;
const scrollBody = (c: HTMLElement) =>
  c.querySelector('[data-testid="privacy-policy-scroll-body"]') as HTMLElement;

/** 실제 Chromium이라 scrollTop을 바닥으로 옮기면 브라우저가 진짜 scroll 이벤트를 낸다 */
const scrollToBottom = (el: HTMLElement) => {
  el.scrollTop = el.scrollHeight;
};

beforeEach(() => {
  vi.mocked(signup).mockReset();
  vi.mocked(checkEmailAvailability).mockReset();
  vi.mocked(checkEmailAvailability).mockResolvedValue(true);
});

describe('회원가입 폼 — 개인정보처리방침 동의', () => {
  it('처음에는 체크박스가 비어 있고 회원가입 버튼이 비활성이다', async () => {
    const { container } = await renderSignupForm();

    expect(privacyCheckbox(container).checked).toBe(false);
    expect(submitButton(container).hasAttribute('disabled')).toBe(true);
  });

  it('체크박스를 눌러도 바로 체크되지 않고 방침 모달이 뜬다', async () => {
    const { container, getByRole } = await renderSignupForm();

    await userEvent.click(privacyCheckbox(container));

    expect(privacyCheckbox(container).checked).toBe(false);
    await expect.element(getByRole('dialog')).toBeInTheDocument();
  });

  it('끝까지 스크롤하기 전에는 모달의 확인 버튼이 비활성이다', async () => {
    const { container, getByRole } = await renderSignupForm();

    await userEvent.click(privacyCheckbox(container));

    const confirmButton = getByRole('button', { name: '끝까지 읽어주세요' });
    await expect.element(confirmButton).toBeInTheDocument();
    expect((await confirmButton.element()).hasAttribute('disabled')).toBe(true);
  });

  it('끝까지 스크롤하면 확인 버튼이 활성화되고, 눌러야만 모달이 닫히며 체크박스가 체크된다', async () => {
    const { container, getByRole } = await renderSignupForm();

    await userEvent.click(privacyCheckbox(container));
    scrollToBottom(scrollBody(container));

    const confirmButton = getByRole('button', { name: '확인' });
    await expect.element(confirmButton).toBeInTheDocument();

    await userEvent.click(confirmButton);

    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(privacyCheckbox(container).checked).toBe(true);
    expect(submitButton(container).hasAttribute('disabled')).toBe(false);
  });

  it('배경을 클릭하거나 ESC를 눌러도 모달이 닫히지 않는다', async () => {
    const { container, getByRole } = await renderSignupForm();

    await userEvent.click(privacyCheckbox(container));
    await expect.element(getByRole('dialog')).toBeInTheDocument();

    container
      .querySelector('[role="presentation"]')
      ?.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await userEvent.keyboard('{Escape}');

    await expect.element(getByRole('dialog')).toBeInTheDocument();
  });

  it('동의 후 폼을 채우고 제출하면 signup이 호출된다', async () => {
    vi.mocked(signup).mockResolvedValue({
      id: 1,
      email: 'astronaut@woojuin.com',
      nickname: '우주인',
      profileImageUrl: null,
      provider: 'LOCAL',
      emailVerified: false,
      avatarColor: 'WHITE',
      personalSpaceId: 1,
      personalTutorialCompleted: false,
      sharedWorkspaceTutorialCompleted: false,
    });
    const { container, getByRole } = await renderSignupForm();

    await userEvent.click(privacyCheckbox(container));
    scrollToBottom(scrollBody(container));
    await userEvent.click(getByRole('button', { name: '확인' }));

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement;
    const passwordInput = container.querySelector('input[type="password"]') as HTMLInputElement;
    const nicknameInput = container.querySelector('input[type="text"]') as HTMLInputElement;
    await userEvent.type(emailInput, 'astronaut@woojuin.com');
    await userEvent.type(passwordInput, 'password123');
    await userEvent.type(nicknameInput, '우주인');
    await userEvent.click(submitButton(container));

    await expect.poll(() => vi.mocked(signup).mock.calls.length).toBe(1);
  });
});
