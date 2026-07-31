import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter } from 'react-router-dom';
import SettingsCard from '@/components/domain/mypage/SettingsCard';
import type { AiUsage } from '@/services/auth';

const limitedUsage: AiUsage = {
  period: '2026-07',
  used: 12,
  limit: 30,
  remaining: 18,
  unlimited: false,
  limitEnabled: true,
  resetAt: '2026-08-01T00:00:00+09:00',
};

const renderSettingsCard = (aiUsage: AiUsage) =>
  render(
    <MemoryRouter>
      <SettingsCard
        notificationEnabled
        onNotificationToggle={vi.fn()}
        aiUsage={aiUsage}
        aiUsageLoading={false}
        aiUsageError={false}
      />
    </MemoryRouter>,
  );

describe('마이페이지 설정 카드', () => {
  it('이번 달 AI 사용 횟수와 한도를 표시한다', async () => {
    const { container } = await renderSettingsCard(limitedUsage);

    expect(container.textContent).toContain('이번 달 AI 사용량');
    expect(container.textContent).toContain('12회');
    expect(container.textContent).toContain('30회 한도');

    const progress = container.querySelector('[role="progressbar"]');
    expect(progress?.getAttribute('aria-valuenow')).toBe('12');
    expect(progress?.getAttribute('aria-valuemax')).toBe('30');
  });

  it('무제한 사용자는 누적 횟수와 무제한 상태를 표시한다', async () => {
    const { container } = await renderSettingsCard({
      ...limitedUsage,
      used: 42,
      remaining: null,
      unlimited: true,
    });

    expect(container.textContent).toContain('42회');
    expect(container.textContent).toContain('무제한');
    expect(container.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('문의 이메일과 개인정보 처리방침 링크를 제공한다', async () => {
    const { container } = await renderSettingsCard(limitedUsage);

    expect(container.textContent).not.toContain('이메일 또는 MM으로 연락해 주세요.');

    const helpButton = [...container.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('도움말 및 고객센터'),
    ) as HTMLButtonElement;
    await userEvent.click(helpButton);

    const emailLink = container.querySelector(
      'a[href="mailto:woojuin105@gmail.com"]',
    ) as HTMLAnchorElement;
    const privacyLink = container.querySelector('a[href="/privacy"]') as HTMLAnchorElement;

    expect(container.textContent).toContain('이메일 또는 MM으로 연락해 주세요.');
    expect(emailLink.textContent).toContain('woojuin105@gmail.com');
    expect(privacyLink.textContent).toContain('개인정보 처리방침');
  });
});
