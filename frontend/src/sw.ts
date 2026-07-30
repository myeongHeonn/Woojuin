/// <reference lib="webworker" />
/**
 * 커스텀 서비스워커 (injectManifest 전략)
 * - precache: vite-plugin-pwa가 빌드 산출물 주입
 * - FCM 백그라운드 수신(FR-050): Firebase 프로젝트 생성 후 아래 TODO 구현
 */
import { precacheAndRoute, cleanupOutdatedCaches } from 'workbox-precaching';

declare let self: ServiceWorkerGlobalScope;

cleanupOutdatedCaches();
precacheAndRoute(self.__WB_MANIFEST);

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting();
});

// FCM Admin SDK가 Notification 메시지로 보내므로, 실제 웹푸시 배달 payload는
// { notification: { title, body }, data: {...} } 형태로 온다 (data-only 메시지가
// 아니라서 최상위 payload.title 은 없음 — firebase-admin의 Message.setNotification 참고).
self.addEventListener('push', (event) => {
  console.log('[sw] push event received', event.data ? 'has data' : 'no data');
  event.waitUntil(
    (async () => {
      try {
        const payload = event.data?.json() ?? {};
        console.log('[sw] push payload', JSON.stringify(payload));
        const title = payload.notification?.title ?? payload.title ?? '우주인';
        const body = payload.notification?.body ?? payload.body ?? 'AI 정리가 완료됐어요.';
        await self.registration.showNotification(title, {
          body,
          data: payload.data,
        });
        console.log('[sw] showNotification 호출 완료');
      } catch (err) {
        console.error('[sw] push 처리 중 에러', err);
      }
    })(),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const itemUrl: string = event.notification.data?.url ?? '/';
  event.waitUntil(self.clients.openWindow(itemUrl));
});
