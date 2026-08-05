import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ConnectedAppsCard from '@/components/domain/mypage/ConnectedAppsCard';
import {
  disconnectChatConnection,
  fetchChatConnections,
  type ChatConnection,
} from '@/services/integrations';

vi.mock('@/services/integrations', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/integrations')>()),
  fetchChatConnections: vi.fn(),
  disconnectChatConnection: vi.fn(),
  issueChatLinkCode: vi.fn(), // 내부의 ChatIntegrationCard(연결하기 모달)가 쓴다
}));

const discordConnection: ChatConnection = {
  id: 11,
  platform: 'DISCORD',
  connectedAt: new Date(Date.now() - 3 * 24 * 60 * 60_000).toISOString(),
  defaultWorkspaceId: 7,
  defaultWorkspaceName: '몽골 여행',
};

const renderCard = (onError = vi.fn()) =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <ConnectedAppsCard onError={onError} />
    </QueryClientProvider>,
  );

const modal = (c: HTMLElement) => c.querySelector('[role="alertdialog"]') as HTMLElement | null;

beforeEach(() => {
  vi.mocked(fetchChatConnections).mockReset().mockResolvedValue([discordConnection]);
  vi.mocked(disconnectChatConnection).mockReset().mockResolvedValue(undefined);
});

describe('연결된 앱 카드', () => {
  it('연동 목록에 플랫폼·연결 시각·기본 워크스페이스를 보여준다', async () => {
    const { container } = await renderCard();
    await vi.waitFor(() => expect(container.textContent).toContain('Discord'));

    expect(container.textContent).toContain('3일 전 연결');
    expect(container.textContent).toContain('기본: 몽골 여행');
    expect(container.textContent).toContain('연결 해제');
  });

  it('연동이 없으면 빈 목록 대신 연결 안내와 연결하는 길을 보여준다', async () => {
    vi.mocked(fetchChatConnections).mockResolvedValue([]);
    const { container } = await renderCard();

    await vi.waitFor(() => expect(container.textContent).toContain('연결된 채팅 앱이 없습니다.'));
    expect(container.textContent).toContain('채팅 앱 연동'); // ChatIntegrationCard 진입점
  });

  it('연결 해제는 확인을 거쳐 해제하고 목록을 다시 받는다', async () => {
    const { container } = await renderCard();
    await vi.waitFor(() => expect(container.textContent).toContain('연결 해제'));

    const disconnectButton = [...container.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('연결 해제'),
    ) as HTMLButtonElement;
    await userEvent.click(disconnectButton);

    expect(modal(container)?.textContent).toContain('Discord 연동을 해제할까요?');
    expect(modal(container)?.textContent).toContain('더 이상 저장할 수 없습니다');

    const confirm = [...(modal(container)?.querySelectorAll('button') ?? [])].find((b) =>
      b.textContent?.includes('연결 해제'),
    ) as HTMLButtonElement;
    vi.mocked(fetchChatConnections).mockResolvedValue([]);
    await userEvent.click(confirm);

    await vi.waitFor(() => expect(disconnectChatConnection).toHaveBeenCalledWith(11));
    await vi.waitFor(() => expect(container.textContent).toContain('연결된 채팅 앱이 없습니다.'));
  });

  it('목록을 못 받아오면 오류와 재시도 버튼을 보여준다', async () => {
    vi.mocked(fetchChatConnections)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce([discordConnection]);
    const { container } = await renderCard();

    await vi.waitFor(() =>
      expect(container.textContent).toContain('연동 목록을 불러오지 못했습니다.'),
    );

    const retry = [...container.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('다시 시도'),
    ) as HTMLButtonElement;
    await userEvent.click(retry);

    await vi.waitFor(() => expect(container.textContent).toContain('Discord'));
  });

  it('해제가 실패하면 오류를 알린다', async () => {
    vi.mocked(disconnectChatConnection).mockRejectedValue(new Error('500'));
    const onError = vi.fn();
    const { container } = await renderCard(onError);
    await vi.waitFor(() => expect(container.textContent).toContain('연결 해제'));

    const disconnectButton = [...container.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('연결 해제'),
    ) as HTMLButtonElement;
    await userEvent.click(disconnectButton);
    const confirm = [...(modal(container)?.querySelectorAll('button') ?? [])].find((b) =>
      b.textContent?.includes('연결 해제'),
    ) as HTMLButtonElement;
    await userEvent.click(confirm);

    await vi.waitFor(() => expect(onError).toHaveBeenCalled());
  });
});
