import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider, createStore } from 'jotai';
import { AxiosError, AxiosHeaders } from 'axios';
import InvitePage from '@/pages/InvitePage';
import { fetchInvitation, acceptInvitation } from '@/services/workspaces';
import { accessTokenAtom } from '@/stores/authAtoms';

vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  fetchInvitation: vi.fn(),
  acceptInvitation: vi.fn(),
}));

const mockFetchInvitation = vi.mocked(fetchInvitation);
const mockAcceptInvitation = vi.mocked(acceptInvitation);

const axiosErrorWithMessage = (status: number, message: string) =>
  new AxiosError('요청 실패', String(status), undefined, undefined, {
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data: { status, message, data: null },
  });

const renderInvitePage = () => {
  const store = createStore();
  store.set(accessTokenAtom, 'token');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/invite/abc-123']}>
          <Routes>
            <Route path="/invite/:code" element={<InvitePage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>,
  );
};

describe('InvitePage — 추방된 유저의 재입장 실패 메시지', () => {
  it('추방된 이력 때문에 실패하면 "다시 시도" 대신 추방 전용 문구를 보여준다', async () => {
    mockFetchInvitation.mockResolvedValue({
      code: 'abc-123',
      workspaceId: 10,
      workspaceName: '우리팀',
      expiresAt: '',
    });
    mockAcceptInvitation.mockRejectedValue(
      axiosErrorWithMessage(
        403,
        'COMMON_403: 이 워크스페이스에서 추방된 이력이 있어 재입장할 수 없습니다 (workspaceId=10)',
      ),
    );

    const screen = await renderInvitePage();
    await userEvent.click(screen.getByRole('button', { name: '참여하기' }));

    await expect.element(screen.getByText(/추방되어/)).toBeInTheDocument();
    expect(screen.getByText('참여하지 못했어요. 다시 시도해 주세요.').elements()).toHaveLength(0);
  });

  it('추방과 무관한 다른 실패는 기존처럼 "다시 시도" 문구를 보여준다', async () => {
    mockFetchInvitation.mockResolvedValue({
      code: 'abc-123',
      workspaceId: 10,
      workspaceName: '우리팀',
      expiresAt: '',
    });
    mockAcceptInvitation.mockRejectedValue(
      axiosErrorWithMessage(500, 'COMMON_500: 알 수 없는 오류'),
    );

    const screen = await renderInvitePage();
    await userEvent.click(screen.getByRole('button', { name: '참여하기' }));

    await expect
      .element(screen.getByText('참여하지 못했어요. 다시 시도해 주세요.'))
      .toBeInTheDocument();
  });
});
