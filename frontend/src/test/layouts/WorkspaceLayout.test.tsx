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
vi.mock('@/hooks/useWorkspaceEvictionGuard', () => ({
  useWorkspaceEvictionGuard: () => ({ evicted: mockEvicted }),
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
  it('추방되지 않았으면 모달 없이 화면을 그대로 보여준다', async () => {
    mockEvicted = false;

    const screen = await renderLayout();

    await expect.element(screen.getByText('우주 화면')).toBeInTheDocument();
    expect(screen.getByRole('alertdialog').elements()).toHaveLength(0);
  });

  it('추방됐으면 확인 모달을 보여준다', async () => {
    mockEvicted = true;

    const screen = await renderLayout();

    await expect.element(screen.getByRole('alertdialog')).toBeInTheDocument();
    await expect.element(screen.getByText(/추방/)).toBeInTheDocument();
  });

  it('확인 버튼을 누르면 /home으로 이동한다', async () => {
    mockEvicted = true;
    mockNavigate.mockClear();

    const screen = await renderLayout();
    await userEvent.click(screen.getByRole('button', { name: '확인' }));

    expect(mockNavigate).toHaveBeenCalledWith('/home');
  });
});
