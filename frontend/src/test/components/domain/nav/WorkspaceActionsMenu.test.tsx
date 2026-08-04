import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-react';
import { MemoryRouter } from 'react-router-dom';
import { userEvent } from 'vitest/browser';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SideBar from '@/components/domain/nav/SideBar';
import { fetchMembers, getCategories, updateWorkspace } from '@/services/workspaces';
import { fetchItems } from '@/services/items';
import type { Workspace } from '@/services/workspaces';
import type { UserProfile } from '@/services/auth';

/**
 * ⋮ 메뉴는 사이드바 행 위에 겹쳐 놓는 것이라, 컴포넌트 하나만 떼어 보면 정작 중요한 것
 * (링크와 겹치지 않는지, 선택 점과 부딪히지 않는지, 호버에 드러나는지)을 검증할 수 없다.
 * 그래서 사이드바 전체를 실제 브라우저에 올려서 확인한다.
 *
 * 삭제 모달이 열릴 때 영향 요약을 불러오므로 그 호출만 갈아끼운다.
 */
vi.mock('@/services/workspaces', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/workspaces')>()),
  fetchMembers: vi.fn(),
  getCategories: vi.fn(),
  updateWorkspace: vi.fn(),
}));
vi.mock('@/services/items', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/items')>()),
  fetchItems: vi.fn(),
}));

const PROFILE: UserProfile = {
  id: 1,
  email: 'woojuin@example.com',
  nickname: '우주인',
  profileImageUrl: null,
  provider: 'LOCAL',
  emailVerified: true,
  avatarColor: 'BLUE',
  personalSpaceId: 1,
  personalTutorialCompleted: true,
  sharedWorkspaceTutorialCompleted: true,
};

/** id 2 는 내가 OWNER, id 3 은 MEMBER — 메뉴가 역할로 갈리는지 보려면 둘이 다 필요하다 */
const WORKSPACES: Workspace[] = [
  { id: 1, name: 'Personal Space', type: 'PERSONAL', role: 'OWNER' },
  { id: 2, name: '몽골 여행', type: 'TEAM', role: 'OWNER' },
  { id: 3, name: '팀 프로젝트', type: 'TEAM', role: 'MEMBER' },
];

const renderSideBar = (path = '/workspace/2') => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(['workspaces'], WORKSPACES);
  queryClient.setQueryData(['user', 'me'], PROFILE);

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <SideBar />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

/** 행(링크)과 그 옆 ⋮ 버튼 — 둘은 형제다 */
const rowOf = (container: HTMLElement, name: string) =>
  [...container.querySelectorAll('a')].find((a) => a.textContent?.includes(name)) as HTMLElement;
const menuButtonOf = (container: HTMLElement, name: string) =>
  container.querySelector(`button[aria-label="${name} 설정"]`) as HTMLButtonElement | null;

beforeEach(() => {
  vi.mocked(fetchMembers).mockReset().mockResolvedValue([]);
  vi.mocked(getCategories).mockReset().mockResolvedValue([]);
  vi.mocked(updateWorkspace).mockReset();
  vi.mocked(fetchItems)
    .mockReset()
    .mockResolvedValue({ content: [], totalElements: 0, page: 0, size: 1 });
});

describe('사이드바 워크스페이스 ⋮ 메뉴', () => {
  it('내가 OWNER 인 워크스페이스에만 나온다', async () => {
    const { container } = await renderSideBar();

    // 서버가 MEMBER 의 이름 변경·삭제를 403 으로 막으므로, 눌러도 실패할 버튼은 보이면 안 된다
    expect(menuButtonOf(container, '몽골 여행')).not.toBeNull();
    expect(menuButtonOf(container, '팀 프로젝트')).toBeNull();
    // 개인 스페이스는 목록에 없어 애초에 대상이 아니다(삭제 불가)
    expect(menuButtonOf(container, 'Personal Space')).toBeNull();
  });

  it('평소엔 숨어 있고 행에 호버하면 드러난다', async () => {
    const { container } = await renderSideBar();
    const button = menuButtonOf(container, '몽골 여행')!;

    expect(getComputedStyle(button).opacity).toBe('0');

    await userEvent.hover(rowOf(container, '몽골 여행'));
    await vi.waitFor(() => {
      expect(getComputedStyle(button).opacity).toBe('1');
    });
  });

  it('라벨이 ⋮ 밑으로 파고들지 않는다', async () => {
    // 링크에 오른쪽 여백(pr-10)을 미리 잡아 두는 이유가 이것이다. 여백이 사라지면
    // 긴 이름이 버튼 아래로 들어가 글자가 잘린 채 버튼에 가린다
    const { container } = await renderSideBar('/workspace/2');
    const label = [...rowOf(container, '몽골 여행').querySelectorAll('span')].find(
      (span) => span.textContent === '몽골 여행',
    )!;
    const button = menuButtonOf(container, '몽골 여행')!;

    expect(label.getBoundingClientRect().right).toBeLessThanOrEqual(
      button.getBoundingClientRect().left,
    );
  });

  it('선택은 점이 아니라 aria-current 로 알린다', async () => {
    // 펼친 행에서는 점을 그리지 않는다 — 오른쪽 끝은 ⋮ 자리이고, 선택은 배경·글자색으로
    // 이미 드러난다. 의미상 신호(스크린리더가 읽는 것)는 aria-current 가 맡는다
    const { container } = await renderSideBar('/workspace/2');
    const row = rowOf(container, '몽골 여행');

    expect(row.getAttribute('aria-current')).toBe('page');
    expect(row.querySelector('span.bg-current')).toBeNull();
  });

  it('⋮ 를 눌러도 워크스페이스로 이동하지 않는다', async () => {
    // 링크 안에 버튼을 넣으면 이동과 메뉴 열기가 같이 일어난다 — 형제로 둔 이유가 이것이다
    const { container } = await renderSideBar('/workspace/1');
    expect(rowOf(container, 'Personal Space').getAttribute('aria-current')).toBe('page');

    await userEvent.click(menuButtonOf(container, '몽골 여행')!);

    // 선택이 그대로면 이동이 없었다는 뜻이다
    expect(rowOf(container, 'Personal Space').getAttribute('aria-current')).toBe('page');
    expect(container.textContent).toContain('이름 변경');
  });

  it('이름 변경을 고르면 현재 이름이 채워진 폼이 열린다', async () => {
    const { container } = await renderSideBar();

    await userEvent.click(menuButtonOf(container, '몽골 여행')!);
    await userEvent.click(
      [...container.querySelectorAll('button')].find((b) => b.textContent === '이름 변경')!,
    );

    const input = await vi.waitFor(() => {
      const found = container.querySelector(
        'input[aria-label="워크스페이스 이름"]',
      ) as HTMLInputElement;
      expect(found).not.toBeNull();
      return found;
    });
    // 빈 칸으로 시작하면 무엇을 고치는지 모른 채 새로 타이핑해야 한다
    expect(input.value).toBe('몽골 여행');
  });

  it('삭제를 고르면 이름 확인이 있는 삭제 모달이 열린다', async () => {
    const { container } = await renderSideBar();

    await userEvent.click(menuButtonOf(container, '몽골 여행')!);
    await userEvent.click(
      [...container.querySelectorAll('button')].find((b) => b.textContent === '삭제')!,
    );

    // 확인 절차는 기존 모달의 것을 그대로 쓴다(입구가 둘이어도 안전장치는 하나)
    await vi.waitFor(() => {
      expect(container.querySelector('input[aria-label="워크스페이스 이름 확인"]')).not.toBeNull();
    });
  });

  it('사이드바가 접히면 ⋮ 를 그리지 않는다', async () => {
    const { container } = await renderSideBar();
    const toggle = [...container.querySelectorAll('button')].find((b) =>
      /사이드바/.test(b.getAttribute('aria-label') ?? ''),
    )!;

    await userEvent.click(toggle);

    // 접힌 폭에는 아이콘 하나가 겨우 들어간다 — 자리가 없으면 아예 없는 게 맞다
    await vi.waitFor(() => {
      expect(menuButtonOf(container, '몽골 여행')).toBeNull();
    });
  });
});
