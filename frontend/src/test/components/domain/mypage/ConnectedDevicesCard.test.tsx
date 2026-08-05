import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ConnectedDevicesCard from '@/components/domain/mypage/ConnectedDevicesCard';
import {
  fetchSessions,
  revokeAllSessions,
  revokeSession,
  type DeviceSession,
} from '@/services/auth';

// 즉시 차단·세션 종료 분기가 검증 대상 — 네트워크는 전부 갈아끼운다
vi.mock('@/services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/auth')>()),
  fetchSessions: vi.fn(),
  revokeSession: vi.fn(),
  revokeAllSessions: vi.fn(),
}));

const recent = (minutesAgo: number) => new Date(Date.now() - minutesAgo * 60_000).toISOString();

const currentDevice: DeviceSession = {
  sessionId: 'sid-current',
  deviceName: 'Windows · Chrome',
  createdAt: recent(120),
  lastUsedAt: recent(1),
  current: true,
};
const otherDevice: DeviceSession = {
  sessionId: 'sid-other',
  deviceName: 'iPhone · Safari',
  createdAt: recent(600),
  lastUsedAt: recent(180),
  current: false,
};

const renderCard = (onSessionEnded = vi.fn(), onError = vi.fn()) =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <ConnectedDevicesCard onSessionEnded={onSessionEnded} onError={onError} />
    </QueryClientProvider>,
  );

const modal = (c: HTMLElement) => c.querySelector('[role="alertdialog"]') as HTMLElement | null;
const modalConfirm = (c: HTMLElement, label: string) =>
  [...(modal(c)?.querySelectorAll('button') ?? [])].find((b) =>
    b.textContent?.includes(label),
  ) as HTMLButtonElement;
const rowLogoutButton = (c: HTMLElement, deviceName: string) => {
  const row = [...c.querySelectorAll('li')].find((li) => li.textContent?.includes(deviceName));
  return [...(row?.querySelectorAll('button') ?? [])].find((b) =>
    b.textContent?.includes('로그아웃'),
  ) as HTMLButtonElement;
};

beforeEach(() => {
  vi.mocked(fetchSessions).mockReset().mockResolvedValue([currentDevice, otherDevice]);
  vi.mocked(revokeSession).mockReset().mockResolvedValue(undefined);
  vi.mocked(revokeAllSessions).mockReset().mockResolvedValue(undefined);
});

describe('연결된 기기 카드', () => {
  it('기기 목록과 "이 기기" 표시, 마지막 사용 시각을 보여준다', async () => {
    const { container } = await renderCard();
    await vi.waitFor(() => expect(container.textContent).toContain('Windows · Chrome'));

    expect(container.textContent).toContain('iPhone · Safari');
    expect(container.textContent).toContain('이 기기');
    expect(container.textContent).toContain('시간 전'); // otherDevice 의 마지막 사용
    expect(container.textContent).toContain('모든 기기에서 로그아웃');
  });

  it('다른 기기를 해제하면 확인을 거쳐 그 세션만 끊고, 이 기기는 남는다', async () => {
    const onSessionEnded = vi.fn();
    const { container } = await renderCard(onSessionEnded);
    await vi.waitFor(() => expect(container.textContent).toContain('iPhone · Safari'));

    await userEvent.click(rowLogoutButton(container, 'iPhone · Safari'));
    expect(modal(container)?.textContent).toContain('iPhone · Safari 기기가 바로 로그아웃됩니다.');

    await userEvent.click(modalConfirm(container, '로그아웃'));

    await vi.waitFor(() => expect(revokeSession).toHaveBeenCalledWith('sid-other'));
    expect(onSessionEnded).not.toHaveBeenCalled();
  });

  it('현재 기기를 해제하면 로그아웃 안내를 보여주고 세션 종료 처리로 이어진다', async () => {
    const onSessionEnded = vi.fn();
    const { container } = await renderCard(onSessionEnded);
    await vi.waitFor(() => expect(container.textContent).toContain('Windows · Chrome'));

    await userEvent.click(rowLogoutButton(container, 'Windows · Chrome'));
    expect(modal(container)?.textContent).toContain('지금 쓰고 있는 기기입니다');

    await userEvent.click(modalConfirm(container, '로그아웃'));

    await vi.waitFor(() => expect(revokeSession).toHaveBeenCalledWith('sid-current'));
    await vi.waitFor(() => expect(onSessionEnded).toHaveBeenCalled());
  });

  it('"모든 기기에서 로그아웃"은 확인을 거쳐 전체를 끊고 세션 종료 처리로 이어진다', async () => {
    const onSessionEnded = vi.fn();
    const { container } = await renderCard(onSessionEnded);
    await vi.waitFor(() => expect(container.textContent).toContain('모든 기기에서 로그아웃'));

    const allButton = [...container.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('모든 기기에서 로그아웃'),
    ) as HTMLButtonElement;
    await userEvent.click(allButton);
    await userEvent.click(modalConfirm(container, '모두 로그아웃'));

    await vi.waitFor(() => expect(revokeAllSessions).toHaveBeenCalled());
    await vi.waitFor(() => expect(onSessionEnded).toHaveBeenCalled());
  });

  it('목록을 못 받아오면 빈 목록이 아니라 오류와 재시도 버튼을 보여준다', async () => {
    vi.mocked(fetchSessions)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce([currentDevice]);
    const { container } = await renderCard();

    await vi.waitFor(() =>
      expect(container.textContent).toContain('기기 목록을 불러오지 못했습니다.'),
    );
    expect(container.textContent).not.toContain('모든 기기에서 로그아웃');

    const retry = [...container.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('다시 시도'),
    ) as HTMLButtonElement;
    await userEvent.click(retry);

    await vi.waitFor(() => expect(container.textContent).toContain('Windows · Chrome'));
  });

  it('개별 해제가 실패하면 오류를 알리고 세션 종료 처리는 하지 않는다', async () => {
    vi.mocked(revokeSession).mockRejectedValue(new Error('500'));
    const onSessionEnded = vi.fn();
    const onError = vi.fn();
    const { container } = await renderCard(onSessionEnded, onError);
    await vi.waitFor(() => expect(container.textContent).toContain('iPhone · Safari'));

    await userEvent.click(rowLogoutButton(container, 'iPhone · Safari'));
    await userEvent.click(modalConfirm(container, '로그아웃'));

    await vi.waitFor(() => expect(onError).toHaveBeenCalled());
    expect(onSessionEnded).not.toHaveBeenCalled();
  });
});
