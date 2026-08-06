import { Workspace } from '@/api/workspaces';
import { getCachedWorkspaces } from '@/storage/workspaceStorage';

/**
 * 우클릭 메뉴 구성.
 *
 * 이미지 저장은 **저장할 곳을 그 자리에서 고른다.** 팝업에서 고른 스페이스로만 저장되던 때는
 * 다른 곳에 담으려면 팝업을 열어 바꾸고 → 닫고 → 다시 우클릭해야 했다. 이미지는 보던 페이지에서
 * 바로 담는 흐름이라 그 왕복이 저장 자체보다 길었다.
 *
 * 메뉴는 **열리기 전에 다 만들어 두어야 한다** — 메뉴가 열리는 순간에 항목을 채워 넣을 훅이 없고
 * (onClicked 는 사용자가 이미 고른 뒤에 온다), 서비스워커는 유휴 30초면 죽어서 목록을 메모리에
 * 들고 있을 수도 없다. 그래서 storage 캐시(workspaceStorage)를 읽어 짜고, 캐시가 바뀔 때마다
 * 다시 짠다.
 */

export const SAVE_IMAGE_ID = 'save-image';
export const SAVE_SELECTION_ID = 'save-selection';

/** 하위 메뉴 id 는 `save-image:12` 꼴이다. 워크스페이스 id 가 숫자라 ':' 와 겹칠 일이 없다. */
const ID_SEPARATOR = ':';
const IMAGE_SUBMENU_PREFIX = `${SAVE_IMAGE_ID}${ID_SEPARATOR}`;
/** 구분선. 접두사와 겹치지 않아야 아래 파서가 이걸 스페이스로 오해하지 않는다. */
const IMAGE_SEPARATOR_ID = 'save-image-separator';

/** 이미지 저장 메뉴인가 — 하위 메뉴(`save-image:12`)와 단일 메뉴(`save-image`)를 모두 본다. */
export function isImageSaveMenu(menuItemId: string | number): boolean {
  const id = String(menuItemId);
  return id === SAVE_IMAGE_ID || id.startsWith(IMAGE_SUBMENU_PREFIX);
}

/**
 * 하위 메뉴로 고른 스페이스 id. 단일 메뉴로 눌렀으면 null 이고, 그때는 호출부가 팝업에서
 * 고른 스페이스로 떨어진다.
 */
export function workspaceIdFromMenu(menuItemId: string | number): number | null {
  const id = String(menuItemId);
  if (!id.startsWith(IMAGE_SUBMENU_PREFIX)) return null;
  const workspaceId = Number(id.slice(IMAGE_SUBMENU_PREFIX.length));
  return Number.isSafeInteger(workspaceId) && workspaceId > 0 ? workspaceId : null;
}

/**
 * SpacePicker 와 같은 규칙 — Personal Space 는 하나뿐이라 서버가 준 이름('My Space' 등) 대신
 * 고정 문구를 쓴다. 팝업과 메뉴에서 같은 이름으로 보여야 같은 곳이라는 걸 안다.
 */
const workspaceLabel = (workspace: Workspace) =>
  workspace.type === 'PERSONAL' ? 'Personal Space' : workspace.name;

/**
 * 저장한 곳의 이름. 캐시에 없으면 null 이고, 그때 호출부는 스페이스를 뺀 문구로 알린다 —
 * 이름을 얻으려고 저장 피드백을 네트워크 왕복만큼 늦출 이유가 없다.
 */
export async function findWorkspaceLabel(workspaceId: number): Promise<string | null> {
  const workspaces = await getCachedWorkspaces();
  const workspace = workspaces.find((candidate) => candidate.id === workspaceId);
  return workspace ? workspaceLabel(workspace) : null;
}

function createImageMenu(workspaces: Workspace[]): void {
  chrome.contextMenus.create({
    id: SAVE_IMAGE_ID,
    title: '선택한 이미지를 우주인에 저장',
    contexts: ['image'],
  });

  // 고를 게 없으면 하위 메뉴는 클릭만 한 번 늘린다 — 스페이스가 하나뿐이거나 아직 목록을
  // 못 받았으면(미로그인) 단일 항목으로 두고, 저장 시점에 팝업에서 고른 곳으로 떨어진다.
  if (workspaces.length < 2) return;

  const personal = workspaces.filter((workspace) => workspace.type === 'PERSONAL');
  const shared = workspaces.filter((workspace) => workspace.type !== 'PERSONAL');

  const createChild = (workspace: Workspace) => chrome.contextMenus.create({
    id: `${IMAGE_SUBMENU_PREFIX}${workspace.id}`,
    parentId: SAVE_IMAGE_ID,
    title: workspaceLabel(workspace),
    contexts: ['image'],
  });

  personal.forEach(createChild);
  // 팝업 SpacePicker 가 'Workspaces' 헤더로 가르는 자리 — 메뉴에는 그룹 헤더가 없어 선으로 나눈다
  if (personal.length && shared.length) {
    chrome.contextMenus.create({
      id: IMAGE_SEPARATOR_ID,
      parentId: SAVE_IMAGE_ID,
      type: 'separator',
      contexts: ['image'],
    });
  }
  shared.forEach(createChild);
}

/**
 * removeAll 은 콜백 API 다 — 반환값이 void 라 그냥 await 하면 삭제가 끝나기 전에 다음 줄로
 * 넘어가고, 아직 남아 있는 항목과 같은 id 를 만들다 실패한다. 콜백을 promise 로 감싼다.
 */
const removeAllMenus = () => new Promise<void>((resolve) => chrome.contextMenus.removeAll(resolve));

async function buildContextMenus(): Promise<void> {
  const workspaces = await getCachedWorkspaces();
  await removeAllMenus();
  createImageMenu(workspaces);
  chrome.contextMenus.create({
    id: SAVE_SELECTION_ID,
    title: '선택한 텍스트를 우주인에 저장',
    contexts: ['selection'],
  });
}

let queue: Promise<void> = Promise.resolve();

/**
 * 메뉴를 다시 짠다.
 *
 * 호출이 겹칠 수 있어(설치·기동·캐시 변경이 연달아 온다) 직렬화한다 — removeAll 과 create 가
 * 서로 끼어들면 이미 있는 id 를 다시 만들다 실패하고, 그 뒤 항목이 통째로 빠진 메뉴가 남는다.
 */
export function refreshContextMenus(): Promise<void> {
  queue = queue.catch(() => undefined).then(buildContextMenus);
  return queue;
}
