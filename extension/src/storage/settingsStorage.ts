/**
 * 사용자 설정. 지금은 알림 하나뿐이라 파일이 작지만, 팝업과 서비스워커가 같은 키를 봐야 해서
 * (알림을 띄우는 쪽은 서비스워커다) 한곳에 모아 둔다.
 */
const NOTIFY_ON_SAVE_KEY = 'notifyOnSave';

/** 기본값은 켜짐 — 저장은 완료까지 시간이 걸려서 알림이 없으면 끝났는지 알 방법이 없다. */
export async function isNotifyOnSaveEnabled(): Promise<boolean> {
  const result = await chrome.storage.local.get(NOTIFY_ON_SAVE_KEY);
  return result[NOTIFY_ON_SAVE_KEY] !== false;
}

export async function setNotifyOnSaveEnabled(enabled: boolean): Promise<void> {
  await chrome.storage.local.set({ [NOTIFY_ON_SAVE_KEY]: enabled });
}
