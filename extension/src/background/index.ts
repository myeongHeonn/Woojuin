import { ApiError } from '@/api/client';
import { saveImage, saveMemo } from '@/api/items';
import { getAccessToken, getRefreshToken } from '@/storage/authStorage';
import { getSelectedWorkspaceId } from '@/storage/workspaceStorage';

const IMAGE_MAX_BYTES = 10 * 1024 * 1024;
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp']);
const activeSaves = new Set<string>();
const FEEDBACK_KEY = 'contextSaveFeedback';
const NOTIFICATION_ICON = chrome.runtime.getURL('icon128.png');

function truncate(value: string, maxLength: number): string {
  return value.length <= maxLength ? value : `${value.slice(0, maxLength - 1).trimEnd()}…`;
}

function sourceLabel(tab?: chrome.tabs.Tab): string {
  const pageTitle = tab?.title?.replace(/\s+/g, ' ').trim();
  if (pageTitle) return truncate(pageTitle, 30);
  try {
    return new URL(tab?.url ?? '').hostname || '웹페이지';
  } catch {
    return '웹페이지';
  }
}

function memoTitle(content: string, tab?: chrome.tabs.Tab): string {
  const firstSentence = content.replace(/\s+/g, ' ').trim().split(/[.!?\n]/, 1)[0]?.trim();
  if (!firstSentence) return `${sourceLabel(tab)}에서 저장한 메모`;
  return truncate(`${sourceLabel(tab)} 메모: ${truncate(firstSentence, 35)}`, 70);
}

function imageTitle(tab?: chrome.tabs.Tab): string {
  return `${sourceLabel(tab)}에서 저장한 이미지`;
}

function registerMenus(): void {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({ id: 'save-image', title: '선택한 이미지를 우주인에 저장', contexts: ['image'] });
    chrome.contextMenus.create({ id: 'save-selection', title: '선택한 텍스트를 우주인에 저장', contexts: ['selection'] });
  });
}

chrome.runtime.onInstalled.addListener(registerMenus);
chrome.runtime.onStartup.addListener(registerMenus);

async function showFeedback(success: boolean, message: string): Promise<void> {
  await Promise.all([
    chrome.storage.local.set({
      [FEEDBACK_KEY]: { success, message, createdAt: Date.now() },
    }),
    chrome.action.setBadgeBackgroundColor({ color: success ? '#16784b' : '#c33030' }),
    chrome.action.setBadgeText({ text: success ? 'OK' : '!' }),
    chrome.action.setTitle({ title: message }),
    chrome.notifications.create(`context-save-${Date.now()}`, {
      type: 'basic',
      iconUrl: NOTIFICATION_ICON,
      title: success ? '우주인 저장 완료' : '우주인 저장 실패',
      message,
      priority: 1,
    }),
  ]);
  setTimeout(() => void chrome.action.setBadgeText({ text: '' }), 8000);
}

async function requireSaveContext(): Promise<number> {
  const [accessToken, refreshToken, workspaceId] = await Promise.all([
    getAccessToken(),
    getRefreshToken(),
    getSelectedWorkspaceId(),
  ]);
  if (!accessToken && !refreshToken) throw new ApiError('로그인이 필요합니다.', 401);
  if (!workspaceId) throw new Error('팝업에서 워크스페이스를 먼저 선택해 주세요.');
  return workspaceId;
}

async function downloadImage(srcUrl: string, permission: Promise<boolean>): Promise<File> {
  const url = new URL(srcUrl);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('data: 및 blob: 이미지는 저장할 수 없습니다.');
  const permitted = await permission;
  if (!permitted) throw new Error('이미지를 내려받을 사이트 권한이 필요합니다.');

  const response = await fetch(srcUrl, { credentials: 'include' });
  if (!response.ok) throw new Error('이미지 다운로드에 실패했습니다.');
  if (Number(response.headers.get('content-length') || 0) > IMAGE_MAX_BYTES) {
    throw new Error('이미지는 10MB 이하여야 합니다.');
  }
  const blob = await response.blob();
  const contentType = blob.type.toLowerCase().split(';')[0];
  if (!IMAGE_TYPES.has(contentType)) {
    throw new Error(`지원하지 않는 이미지 형식입니다. (${contentType || '형식 정보 없음'})`);
  }
  if (blob.size > IMAGE_MAX_BYTES) throw new Error('이미지는 10MB 이하여야 합니다.');
  const extension = contentType.split('/')[1].replace('jpeg', 'jpg');
  return new File([blob], `woojuin-image.${extension}`, { type: contentType });
}

async function handleContextSave(
  info: chrome.contextMenus.OnClickData,
  tab?: chrome.tabs.Tab,
  imagePermission?: Promise<boolean>,
): Promise<void> {
  const workspaceId = await requireSaveContext();
  if (info.menuItemId === 'save-selection') {
    const content = info.selectionText?.trim();
    if (!content) throw new Error('빈 텍스트는 저장할 수 없습니다.');
    await saveMemo(workspaceId, content, memoTitle(content, tab));
  } else if (info.menuItemId === 'save-image' && info.srcUrl) {
    if (!imagePermission) throw new Error('이미지 출처 권한을 요청하지 못했습니다.');
    await saveImage(workspaceId, await downloadImage(info.srcUrl, imagePermission), imageTitle(tab));
  }
}

chrome.contextMenus.onClicked.addListener((info, tab) => {
  // permissions.request는 사용자 제스처가 유지되는 동기 이벤트 구간에서 즉시 호출해야 한다.
  // 인증/워크스페이스 조회를 await한 뒤 호출하면 Chrome이 요청을 거부한다.
  let imagePermission: Promise<boolean> | undefined;
  if (info.menuItemId === 'save-image' && info.srcUrl) {
    const url = new URL(info.srcUrl);
    if (url.protocol === 'http:' || url.protocol === 'https:') {
      imagePermission = chrome.permissions.request({ origins: [`${url.origin}/*`] });
    }
  }
  const requestKey = `${String(info.menuItemId)}:${info.srcUrl ?? info.selectionText?.trim() ?? ''}`;
  if (activeSaves.has(requestKey)) return;
  activeSaves.add(requestKey);
  void handleContextSave(info, tab, imagePermission)
    .then(() => showFeedback(true, '우주인으로 보냈습니다.'))
    .catch((error: unknown) => {
      console.error('우주인 저장 실패:', error);
      const message = error instanceof Error ? error.message : '우클릭 저장에 실패했습니다.';
      return showFeedback(false, message);
    })
    .finally(() => activeSaves.delete(requestKey));
});
