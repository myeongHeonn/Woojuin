import { Workspace } from '@/api/workspaces';

const WORKSPACE_KEY = 'selectedWorkspaceId';

/**
 * 워크스페이스 목록 캐시.
 *
 * 우클릭 메뉴는 **미리 만들어 두어야 한다** — 메뉴가 열리는 순간에는 항목을 채워 넣을 훅이 없고
 * (onClicked 는 이미 고른 뒤에 온다), 서비스워커는 유휴 30초면 죽어서 메모리에 목록을 들고
 * 있을 수도 없다. 그래서 목록을 여기에 남겨 두고, 워커가 깨어날 때마다 이걸 읽어 메뉴를 짠다.
 */
export const WORKSPACE_LIST_KEY = 'workspaceList';

export async function getSelectedWorkspaceId(): Promise<number | null> {
  const result = await chrome.storage.local.get(WORKSPACE_KEY);
  return typeof result[WORKSPACE_KEY] === 'number' ? result[WORKSPACE_KEY] : null;
}

export async function setSelectedWorkspaceId(workspaceId: number): Promise<void> {
  await chrome.storage.local.set({ [WORKSPACE_KEY]: workspaceId });
}

export async function clearSelectedWorkspaceId(): Promise<void> {
  await chrome.storage.local.remove(WORKSPACE_KEY);
}

export async function getCachedWorkspaces(): Promise<Workspace[]> {
  const result = await chrome.storage.local.get(WORKSPACE_LIST_KEY);
  const cached = result[WORKSPACE_LIST_KEY];
  return Array.isArray(cached) ? (cached as Workspace[]) : [];
}

export async function setCachedWorkspaces(workspaces: Workspace[]): Promise<void> {
  await chrome.storage.local.set({ [WORKSPACE_LIST_KEY]: workspaces });
}

export async function clearCachedWorkspaces(): Promise<void> {
  await chrome.storage.local.remove(WORKSPACE_LIST_KEY);
}
