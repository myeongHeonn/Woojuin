const WORKSPACE_KEY = 'selectedWorkspaceId';

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
