import { describe, it, expect } from 'vitest';
import { render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import WorkspaceSwitcher from '@/components/domain/nav/WorkspaceSwitcher';
import type { Workspace } from '@/services/workspaces';

const WORKSPACES: Workspace[] = [
  { id: 1, name: '개인', type: 'PERSONAL', role: 'OWNER' },
  { id: 2, name: '몽골 여행', type: 'TEAM', role: 'OWNER' },
];

function Loc() {
  const { pathname } = useLocation();
  return <span data-testid="loc">{pathname}</span>;
}
const loc = (c: HTMLElement) => c.querySelector('[data-testid="loc"]')?.textContent;

const renderAt = (path: string) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  qc.setQueryData(['workspaces'], WORKSPACES);
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/workspace/:workspaceId/*"
            element={
              <>
                <WorkspaceSwitcher />
                <Loc />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

describe('WorkspaceSwitcher', () => {
  it('현재 워크스페이스 이름을 보이고, 다른 워크스페이스를 고르면 같은 뷰로 전환한다', async () => {
    const { container } = await renderAt('/workspace/1/library');
    expect(container.textContent).toContain('개인');

    await userEvent.click(container.querySelector('button[aria-label*="워크스페이스 전환"]')!);
    const other = [...container.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('몽골 여행'),
    )!;
    await userEvent.click(other);

    // 워크스페이스만 2로 바뀌고 보던 뷰(library)는 유지
    await expect.poll(() => loc(container)).toBe('/workspace/2/library');
  });

  /**
   * WCAG "Label in Name" — 접근성 이름은 화면에 보이는 글자를 포함해야 한다.
   * 안 그러면 음성 제어 사용자가 보이는 이름("개인")을 불러도 이 버튼이 안 잡힌다.
   */
  it('접근성 이름에 화면에 보이는 워크스페이스 이름이 들어간다', async () => {
    const { container } = await renderAt('/workspace/1/library');

    const trigger = container.querySelector('button[aria-label*="워크스페이스 전환"]')!;
    const visibleName = trigger.textContent!.trim();

    expect(visibleName).not.toBe('');
    expect(trigger.getAttribute('aria-label')).toContain(visibleName);
  });
});
