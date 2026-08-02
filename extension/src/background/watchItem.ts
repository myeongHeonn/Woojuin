import { WEB_ORIGIN } from '@/api/client';
import { getItemStatus, isTerminal, ItemStatus } from '@/api/items';
import { isNotifyOnSaveEnabled } from '@/storage/settingsStorage';

/**
 * 저장한 아이템이 **실제로 처리 완료될 때까지** 지켜보고 알린다.
 *
 * 저장 API 는 접수만 하고 PROCESSING 으로 즉시 돌아온다(크롤·AI 요약·분류는 워커가 한다).
 * 그래서 응답 시점에 "저장 완료" 라고 알리면 거짓말이 된다 — 상태가 PROCESSING 을 벗어날 때까지
 * 폴링한 뒤 알린다.
 *
 * 팝업이 아니라 서비스워커에서 도는 이유: 팝업은 포커스를 잃으면 닫히므로 그 안에서 기다리면
 * 사용자가 다른 곳을 클릭한 순간 감시가 끊긴다.
 *
 * SSE(웹앱이 쓰는 /workspaces/{id}/events)를 쓰지 않은 이유: 서비스워커는 유휴 상태로 30초쯤
 * 뒤 종료되므로 긴 연결을 유지할 수 없다. 짧은 폴링이 이 환경에 맞는다 —
 * 알람(chrome.alarms)이 워커를 다시 깨워 주기 때문이다.
 */

const ALARM_PREFIX = 'watch-item:';
const WATCH_KEY = 'watchedItems';
/**
 * 마지막 처리 결과. 팝업이 열려 있을 때 '분석 중 → 완료' 전환에 쓴다 — 팝업이 따로 폴링하면
 * 같은 API 를 두 곳에서 두드리게 되므로, 감시는 여기 한 곳만 하고 결과를 여기에 남긴다.
 */
export const LAST_RESULT_KEY = 'lastItemResult';
/** 처리는 보통 수 초~수십 초다. 이보다 오래 걸리면 실패했거나 큐가 밀린 것이니 조용히 포기한다. */
const MAX_ATTEMPTS = 40;
/** 알람 최소 주기가 분 단위라 짧은 폴링은 setTimeout 으로 하고, 알람은 워커 부활용으로 쓴다. */
const POLL_INTERVAL_MS = 3000;

interface Watched {
  itemId: number;
  workspaceId: number;
  attempts: number;
}

/**
 * 알림 id 에 워크스페이스를 실어 둔다 — 클릭했을 때 열 주소를 만들려면 필요한데, 서비스워커는
 * 언제든 죽어서 메모리에 들고 있을 수 없다. 별도 저장소를 두는 대신 id 를 그 자체로 쓴다.
 * 아이템 단건 딥링크는 웹앱에 없다(상세 모달이 URL 이 아니라 state 로 열린다) — 그래서 해당
 * 스페이스의 라이브러리를 연다.
 */
const NOTIFICATION_PREFIX = 'woojuin:item:';

async function loadWatched(): Promise<Record<string, Watched>> {
  const result = await chrome.storage.local.get(WATCH_KEY);
  return (result[WATCH_KEY] as Record<string, Watched> | undefined) ?? {};
}

async function saveWatched(watched: Record<string, Watched>): Promise<void> {
  await chrome.storage.local.set({ [WATCH_KEY]: watched });
}

const MESSAGES: Record<Exclude<ItemStatus, 'PROCESSING'>, { title: string; message: string }> = {
  DONE: { title: '우주인 저장 완료', message: 'AI 정리까지 끝났어요. 라이브러리에서 확인해 보세요.' },
  PARTIAL: { title: '우주인 저장 완료', message: '일부 내용만 가져왔어요. 라이브러리에서 확인해 보세요.' },
  FAILED: { title: '우주인 저장 실패', message: '내용을 가져오지 못했어요. 다시 시도해 주세요.' },
};

async function notify(
  status: Exclude<ItemStatus, 'PROCESSING'>,
  workspaceId: number,
): Promise<void> {
  if (!(await isNotifyOnSaveEnabled())) return;
  const { title, message } = MESSAGES[status];
  await chrome.notifications.create(`${NOTIFICATION_PREFIX}${workspaceId}:${Date.now()}`, {
    type: 'basic',
    iconUrl: chrome.runtime.getURL('icon128.png'),
    title,
    message,
    priority: 1,
  });
}

/** 한 번 확인하고, 아직 처리 중이면 다시 예약한다. */
async function poll(itemId: number): Promise<void> {
  const watched = await loadWatched();
  const entry = watched[String(itemId)];
  if (!entry) return; // 이미 끝났거나 취소됨

  try {
    const { status } = await getItemStatus(itemId);
    if (isTerminal(status)) {
      delete watched[String(itemId)];
      await saveWatched(watched);
      await chrome.alarms.clear(`${ALARM_PREFIX}${itemId}`);
      await chrome.storage.local.set({
        [LAST_RESULT_KEY]: { itemId, status, at: Date.now() },
      });
      await notify(status, entry.workspaceId);
      return;
    }
  } catch {
    // 일시적인 실패(네트워크·토큰 갱신 중)는 다음 회차에 다시 본다
  }

  entry.attempts += 1;
  if (entry.attempts >= MAX_ATTEMPTS) {
    delete watched[String(itemId)];
    await saveWatched(watched);
    await chrome.alarms.clear(`${ALARM_PREFIX}${itemId}`);
    return;
  }
  await saveWatched(watched);
  setTimeout(() => void poll(itemId), POLL_INTERVAL_MS);
}

/** 저장 직후 호출한다. 이미 완료 상태로 돌아온 아이템이면 바로 알린다. */
export async function watchItem(
  itemId: number,
  status: ItemStatus,
  workspaceId: number,
): Promise<void> {
  if (isTerminal(status)) {
    await chrome.storage.local.set({ [LAST_RESULT_KEY]: { itemId, status, at: Date.now() } });
    await notify(status, workspaceId);
    return;
  }
  const watched = await loadWatched();
  watched[String(itemId)] = { itemId, workspaceId, attempts: 0 };
  await saveWatched(watched);
  // 워커가 중간에 죽어도 알람이 깨워서 폴링을 이어 준다(최소 주기가 1분이라 보조 수단이다).
  await chrome.alarms.create(`${ALARM_PREFIX}${itemId}`, { periodInMinutes: 1 });
  setTimeout(() => void poll(itemId), POLL_INTERVAL_MS);
}

/** 알람으로 워커가 깨어났을 때 이어서 확인한다. */
export function resumeWatchOnAlarm(alarm: chrome.alarms.Alarm): void {
  if (!alarm.name.startsWith(ALARM_PREFIX)) return;
  const itemId = Number(alarm.name.slice(ALARM_PREFIX.length));
  if (Number.isFinite(itemId)) void poll(itemId);
}

/** 알림을 누르면 그 스페이스의 라이브러리를 새 탭으로 연다 — 방금 저장한 게 거기 쌓인다. */
export function openFromNotification(notificationId: string): void {
  if (!notificationId.startsWith(NOTIFICATION_PREFIX)) return;
  const workspaceId = Number(notificationId.slice(NOTIFICATION_PREFIX.length).split(':')[0]);
  const url = Number.isFinite(workspaceId) && workspaceId > 0
    ? `${WEB_ORIGIN}/workspace/${workspaceId}/library`
    : `${WEB_ORIGIN}/home`;
  void chrome.tabs.create({ url });
  void chrome.notifications.clear(notificationId);
}
