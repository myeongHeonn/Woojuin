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

// TODO(FR-050): Firebase Admin에서 발송한 푸시 수신
// importScripts 대신 firebase/messaging/sw 모듈 방식 사용 예정
self.addEventListener('push', (event) => {
  const payload = event.data?.json() ?? {};
  event.waitUntil(
    self.registration.showNotification(payload.title ?? '우주인', {
      body: payload.body ?? 'AI 정리가 완료됐어요.',
      data: payload.data,
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const itemUrl: string = event.notification.data?.url ?? '/';
  event.waitUntil(self.clients.openWindow(itemUrl));
});
