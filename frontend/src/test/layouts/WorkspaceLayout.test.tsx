import { describe, it, expect, vi } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import WorkspaceLayout from '@/layouts/WorkspaceLayout';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

vi.mock('@/hooks/useWorkspaceEvents', () => ({ useWorkspaceEvents: vi.fn() }));

let mockEvicted = false;
let mockAccessDenied = false;
vi.mock('@/hooks/useWorkspaceEvictionGuard', () => ({
  useWorkspaceEvictionGuard: () => ({ evicted: mockEvicted, accessDenied: mockAccessDenied }),
}));

const renderLayout = () =>
  render(
    <MemoryRouter initialEntries={['/workspace/10/universe']}>
      <Routes>
        <Route path="/workspace/:workspaceId" element={<WorkspaceLayout />}>
          <Route path="universe" element={<div>우주 화면</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );

describe('WorkspaceLayout — 추방 감지', () => {
  it('추방되지도 권한이 없지도 않으면 모달 없이 화면을 그대로 보여준다', async () => {
    mockEvicted = false;
    mockAccessDenied = false;

    const screen = await renderLayout();

    await expect.element(screen.getByText('우주 화면')).toBeInTheDocument();
    expect(screen.getByRole('alertdialog').elements()).toHaveLength(0);
  });

  it('추방됐으면 확인 모달을 보여준다', async () => {
    mockEvicted = true;
    mockAccessDenied = false;

    const screen = await renderLayout();

    await expect.element(screen.getByRole('alertdialog')).toBeInTheDocument();
    await expect.element(screen.getByText(/추방/)).toBeInTheDocument();
  });

  it('확인 버튼을 누르면 /home으로 이동한다', async () => {
    mockEvicted = true;
    mockAccessDenied = false;
    mockNavigate.mockClear();

    const screen = await renderLayout();
    await userEvent.click(screen.getByRole('button', { name: '확인' }));

    expect(mockNavigate).toHaveBeenCalledWith('/home');
  });
});

describe('WorkspaceLayout — 접근 권한 없음 감지', () => {
  it('초대 없이 남의 워크스페이스 URL로 바로 들어오면 권한 없음 모달을 보여준다(추방 문구 아님)', async () => {
    mockEvicted = false;
    mockAccessDenied = true;

    const screen = await renderLayout();

    await expect.element(screen.getByRole('alertdialog')).toBeInTheDocument();
    await expect.element(screen.getByText(/권한이 없어요/)).toBeInTheDocument();
    expect(screen.getByText(/추방/).elements()).toHaveLength(0);
  });

  it('확인 버튼을 누르면 /home으로 이동한다', async () => {
    mockEvicted = false;
    mockAccessDenied = true;
    mockNavigate.mockClear();

    const screen = await renderLayout();
    await userEvent.click(screen.getByRole('button', { name: '확인' }));

    expect(mockNavigate).toHaveBeenCalledWith('/home');
  });
});
