import { ApiError, WEB_ORIGIN } from '@/api/client';
import { saveImage, saveMemo } from '@/api/items';
import {
  captureTokensFromCallback,
  closeLoginWindow,
  harvestWebSession,
  OAUTH_CALLBACK_PREFIX,
} from '@/auth/webSession';
import { getAccessToken, getRefreshToken } from '@/storage/authStorage';
import { getSelectedWorkspaceId } from '@/storage/workspaceStorage';

const IMAGE_MAX_BYTES = 10 * 1024 * 1024;
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp']);
const activeSaves = new Set<string>();
const FEEDBACK_KEY = 'contextSaveFeedback';
const NOTIFICATION_ICON = chrome.runtime.getURL('icon128.png');

function registerMenus(): void {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({ id: 'save-image', title: '선택한 이미지를 우주인에 저장', contexts: ['image'] });
    chrome.contextMenus.create({ id: 'save-selection', title: '선택한 텍스트를 우주인에 저장', contexts: ['selection'] });
  });
}

chrome.runtime.onInstalled.addListener(registerMenus);
chrome.runtime.onStartup.addListener(registerMenus);

// 웹앱에서 로그인하면 그 세션을 자동으로 물려받는다 — 사용자가 팝업에서 따로 확인을 누르지
// 않아도 우클릭 저장이 바로 된다. 이미 토큰이 있으면 같은 값으로 덮어써도 무해하다.
//
// 콜백은 status 를 기다리지 않는다: URL 이 바뀌는 순간 토큰이 이미 주소에 들어 있어서
// 페이지가 다 뜨기 전에 채 갈 수 있다(그만큼 창이 빨리 닫힌다).
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  const url = changeInfo.url ?? tab.url;
  if (!url?.startsWith(WEB_ORIGIN)) return;

  if (url.startsWith(OAUTH_CALLBACK_PREFIX)) {
    void captureTokensFromCallback(url).then(async (captured) => {
      if (!captured) return;
      await notifyLoginDone();
      await closeLoginWindow();
    });
    return;
  }

  if (changeInfo.status === 'complete') void harvestWebSession(tabId);
});

/**
 * 로그인 완료를 알린다. 로그인 창이 곧 닫히므로 무언가 표시가 없으면 사용자가 성공했는지
 * 모른다. 저장 결과 피드백(showFeedback)과 섞지 않는 이유: 그쪽 메시지는 팝업이
 * "우클릭 저장: …" 으로 렌더하므로 로그인 문구가 들어가면 말이 안 맞는다.
 */
async function notifyLoginDone(): Promise<void> {
  await Promise.all([
    chrome.action.setBadgeBackgroundColor({ color: '#16784b' }),
    chrome.action.setBadgeText({ text: 'OK' }),
    chrome.action.setTitle({ title: '우주인에 로그인했어요. 아이콘을 눌러 저장하세요.' }),
    chrome.notifications.create(`login-${Date.now()}`, {
      type: 'basic',
      iconUrl: NOTIFICATION_ICON,
      title: '우주인 로그인 완료',
      message: '확장 프로그램 아이콘을 눌러 지금 보는 페이지를 저장하세요.',
      priority: 1,
    }),
  ]);
  setTimeout(() => void chrome.action.setBadgeText({ text: '' }), 8000);
}

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
  imagePermission?: Promise<boolean>,
): Promise<void> {
  const workspaceId = await requireSaveContext();
  if (info.menuItemId === 'save-selection') {
    const content = info.selectionText?.trim();
    if (!content) throw new Error('빈 텍스트는 저장할 수 없습니다.');
    await saveMemo(workspaceId, content);
  } else if (info.menuItemId === 'save-image' && info.srcUrl) {
    if (!imagePermission) throw new Error('이미지 출처 권한을 요청하지 못했습니다.');
    await saveImage(workspaceId, await downloadImage(info.srcUrl, imagePermission));
  }
}

chrome.contextMenus.onClicked.addListener((info) => {
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
  void handleContextSave(info, imagePermission)
    .then(() => showFeedback(true, '우주인으로 보냈습니다.'))
    .catch((error: unknown) => {
      console.error('우주인 저장 실패:', error);
      const message = error instanceof Error ? error.message : '우클릭 저장에 실패했습니다.';
      return showFeedback(false, message);
    })
    .finally(() => activeSaves.delete(requestKey));
});
