import { ApiError, WEB_ORIGIN } from '@/api/client';
import { saveImage, saveMemo } from '@/api/items';
import {
  captureTokensFromCallback,
  closeLoginWindow,
  harvestWebSession,
  OAUTH_CALLBACK_PREFIX,
} from '@/auth/webSession';
import { getAccessToken, getRefreshToken } from '@/storage/authStorage';
import { openFromNotification, resumeWatchOnAlarm, watchItem } from '@/background/watchItem';
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

// 아이템 처리 완료 감시 — 팝업은 닫히면 끝나므로 감시는 여기서 한다(watchItem.ts 참고).
chrome.alarms.onAlarm.addListener(resumeWatchOnAlarm);
chrome.notifications.onClicked.addListener(openFromNotification);
chrome.runtime.onMessage.addListener((message: unknown) => {
  const request = message as {
    type?: string;
    itemId?: number;
    workspaceId?: number;
    status?: 'PROCESSING' | 'DONE' | 'PARTIAL' | 'FAILED';
  };
  if (request?.type !== 'watchItem' || typeof request.itemId !== 'number'
      || typeof request.workspaceId !== 'number' || !request.status) return;
  void watchItem(request.itemId, request.status, request.workspaceId);
});

// 웹앱에서 로그인하면 그 세션을 자동으로 물려받는다 — 사용자가 팝업에서 따로 확인을 누르지
// 않아도 우클릭 저장이 바로 된다. 이미 토큰이 있으면 같은 값으로 덮어써도 무해하다.
//
// 콜백 분기는 **changeInfo.url 이 있는 이벤트만** 본다. onUpdated 는 한 번의 이동에도 여러 번
// 발생하는데(loading·제목·favicon·complete), tab.url 로 매칭하면 그 횟수만큼 중복 처리된다.
// changeInfo.url 은 주소가 실제로 바뀔 때만 채워지므로 이동당 한 번이다.
// status 를 기다리지 않는 이유: URL 이 바뀌는 순간 토큰이 이미 주소에 있어서 페이지가 다 뜨기
// 전에 채 갈 수 있다(그만큼 창이 빨리 닫힌다).
let handledCallbackUrl: string | null = null;

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.url?.startsWith(OAUTH_CALLBACK_PREFIX)) {
    const url = changeInfo.url;
    if (url === handledCallbackUrl) return;
    handledCallbackUrl = url;
    void captureTokensFromCallback(url).then((captured) => {
      // 알림·배지를 띄우지 않는다 — 팝업이 storage 변화를 듣고 스스로 로그인 상태로 바뀌므로
      // 따로 알릴 게 없다.
      if (captured) return closeLoginWindow();
    });
    return;
  }

  if (changeInfo.status === 'complete' && tab.url?.startsWith(WEB_ORIGIN)) {
    void harvestWebSession(tabId).then((harvested) => {
      // 이미 웹에 로그인돼 있었다는 뜻 — OAuth 를 거칠 필요가 없었으므로 로그인 창이 떠 있으면
      // 바로 닫는다. 우리가 만든 windowId 만 닫으니 사용자가 열어 둔 탭은 그대로다.
      // 콜백 경로와 달리 여유를 두지 않는다: 웹 세션은 이미 저장돼 있다.
      if (harvested) return closeLoginWindow(0);
    });
  }
});

/**
 * 우클릭 저장의 **접수** 결과를 알린다.
 *
 * 성공은 배지·툴팁으로만 알린다 — 완료 알림은 실제 처리가 끝난 뒤 watchItem 이 띄우므로,
 * 여기서도 알림을 띄우면 한 번의 저장에 알림이 두 번 뜬다.
 * 실패는 알림으로 띄운다: 사용자가 조치해야 하고(권한 거부·미로그인 등) 배지만으로는 못 알아챈다.
 * 그래서 실패 알림은 '완료 알림 받기' 설정과 무관하게 항상 띄운다.
 */
async function showFeedback(success: boolean, message: string): Promise<void> {
  await Promise.all([
    chrome.storage.local.set({
      [FEEDBACK_KEY]: { success, message, createdAt: Date.now() },
    }),
    chrome.action.setBadgeBackgroundColor({ color: success ? '#16784b' : '#c33030' }),
    chrome.action.setBadgeText({ text: success ? 'OK' : '!' }),
    chrome.action.setTitle({ title: message }),
    ...(success ? [] : [
      chrome.notifications.create(`context-save-failed-${Date.now()}`, {
        type: 'basic',
        iconUrl: NOTIFICATION_ICON,
        title: '우주인 저장 실패',
        message,
        priority: 1,
      }),
    ]),
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
  // 저장은 접수까지만이고 완료 알림은 watchItem 이 실제 처리가 끝난 뒤에 띄운다.
  if (info.menuItemId === 'save-selection') {
    const content = info.selectionText?.trim();
    if (!content) throw new Error('빈 텍스트는 저장할 수 없습니다.');
    const created = await saveMemo(workspaceId, content);
    await watchItem(created.itemId, created.status, workspaceId);
  } else if (info.menuItemId === 'save-image' && info.srcUrl) {
    if (!imagePermission) throw new Error('이미지 출처 권한을 요청하지 못했습니다.');
    const created = await saveImage(workspaceId, await downloadImage(info.srcUrl, imagePermission));
    await watchItem(created.itemId, created.status, workspaceId);
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
    .then(() => showFeedback(true, '우주인으로 보냈어요. 정리가 끝나면 알려드릴게요.'))
    .catch((error: unknown) => {
      console.error('우주인 저장 실패:', error);
      const message = error instanceof Error ? error.message : '우클릭 저장에 실패했습니다.';
      return showFeedback(false, message);
    })
    .finally(() => activeSaves.delete(requestKey));
});
