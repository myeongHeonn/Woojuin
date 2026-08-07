import type { Messaging } from 'firebase/messaging';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FCM_API_KEY,
  authDomain: import.meta.env.VITE_FCM_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FCM_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FCM_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FCM_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FCM_APP_ID,
};

const vapidKey = import.meta.env.VITE_FCM_VAPID_KEY;

let messagingPromise: Promise<Messaging | null> | null = null;

/**
 * firebase 는 **동적 import** 다. 정적으로 부르면 초기 번들에 통째로 들어가, 알림을
 * 켜지 않은 사용자도 로그인 즉시 받게 된다(2026-08-04: 엔트리 474KB 안에 포함돼 있었다).
 * 여기서 받으면 실제로 푸시 토큰이 필요한 순간에만 내려온다.
 *
 * Safari 구버전 등 Push API 미지원 환경에서 getMessaging()이 바로 던지므로
 * isSupported()로 먼저 가드한다.
 */
async function getMessagingInstance(): Promise<Messaging | null> {
  messagingPromise ??= (async () => {
    const { isSupported, getMessaging } = await import('firebase/messaging');
    if (!(await isSupported())) return null;
    const { initializeApp } = await import('firebase/app');
    return getMessaging(initializeApp(firebaseConfig));
  })();
  return messagingPromise;
}

/**
 * 알림 권한을 요청하고 FCM 토큰을 발급받는다.
 * 이미 커스텀 서비스워커(src/sw.ts, vite-plugin-pwa injectManifest)가 '/' 스코프에
 * 등록돼 있으므로 별도 firebase-messaging-sw.js 없이 그 registration을 그대로 쓴다.
 */
export async function requestPushToken(): Promise<string | null> {
  // 이미 거부된 상태면 물어봐야 결과가 같다 — firebase 를 받지 않고 여기서 끝낸다.
  // (기본값/허용 상태는 아래 흐름 그대로라 프롬프트 동작이 바뀌지 않는다)
  if (typeof Notification !== 'undefined' && Notification.permission === 'denied') return null;

  const messaging = await getMessagingInstance();
  if (!messaging) return null;

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return null;

  const registration = await navigator.serviceWorker.ready;
  try {
    const { getToken } = await import('firebase/messaging');
    return await getToken(messaging, { vapidKey, serviceWorkerRegistration: registration });
  } catch {
    return null;
  }
}

/** 탭이 포그라운드일 때 도착하는 푸시 — 백그라운드 수신은 src/sw.ts의 push 리스너가 처리한다. */
export async function onForegroundPush(callback: (title: string, body: string) => void) {
  const messaging = await getMessagingInstance();
  if (!messaging) return;

  const { onMessage } = await import('firebase/messaging');
  onMessage(messaging, (payload) => {
    callback(payload.notification?.title ?? '우주인', payload.notification?.body ?? '');
  });
}
