/// <reference lib="webworker" />
/**
 * 커스텀 서비스워커 (injectManifest 전략)
 * - precache: vite-plugin-pwa가 빌드 산출물 주입
 * - FCM 백그라운드 수신(FR-050): Firebase 프로젝트 생성 후 아래 TODO 구현
 */
import { precacheAndRoute, cleanupOutdatedCaches } from 'workbox-precaching';
import {
  SHARE_FILE_CACHE,
  SHARE_FILE_KEY_PREFIX,
  SHARE_FILE_NAME_HEADER,
  SHARE_FILES_FLAG,
} from '@/constants/shareTarget';

declare let self: ServiceWorkerGlobalScope;

cleanupOutdatedCaches();
precacheAndRoute(self.__WB_MANIFEST);

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting();
});

/*
 * ── Web Share Target (POST) ─────────────────────────────────────────────────
 *
 * manifest 가 `method: POST` 인 이유는 **GET 이 파일을 받을 수 없어서**다(사진 공유 목록에
 * 우주인이 안 뜬다). 대가로 텍스트·링크까지 POST 로 오는데, 그건 주소 이동이 아니라 진짜
 * 요청이라 SPA 가 직접 받을 수 없다 — 아무도 안 잡으면 서버로 흘러가 404 가 된다.
 *
 * 그래서 여기서 가로채 **GET 시절과 똑같은 모양으로 되돌린다**:
 *
 *   텍스트·링크  →  /share-target?title=&text=&url=   (화면 코드는 예전 그대로)
 *   사진        →  파일을 캐시에 넣고 /share-target?shared=files
 *
 * 303 을 쓰는 이유: 리다이렉트를 GET 으로 바꿔 주는 상태 코드다(307·308 은 POST 를 유지해
 * 같은 요청이 다시 온다).
 */
const SHARE_TARGET_PATH = '/share-target';

const toAbsolute = (pathWithQuery: string, origin: string) =>
  new URL(pathWithQuery, origin).toString();

/** 공유로 들어온 파일들을 캐시에 넣는다. 지난 공유의 잔여물은 먼저 비운다 */
const stashSharedFiles = async (files: File[]) => {
  const cache = await caches.open(SHARE_FILE_CACHE);
  for (const request of await cache.keys()) await cache.delete(request);

  await Promise.all(
    files.map((file, index) =>
      cache.put(
        `${SHARE_FILE_KEY_PREFIX}${index}`,
        new Response(file, {
          headers: {
            'content-type': file.type || 'application/octet-stream',
            // 한글 파일명이 헤더에 그대로 못 들어가므로 인코딩해서 넘긴다
            [SHARE_FILE_NAME_HEADER]: encodeURIComponent(file.name || `shared-${index}`),
          },
        }),
      ),
    ),
  );
};

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  // 이 핸들러는 공유 POST 하나만 본다 — 나머지는 respondWith 를 부르지 않으므로
  // 브라우저가 평소대로 처리한다(프리캐시 라우팅도 그대로 동작한다)
  if (event.request.method !== 'POST' || url.pathname !== SHARE_TARGET_PATH) return;

  event.respondWith(
    (async () => {
      try {
        const form = await event.request.formData();
        // size 0 을 걸러내는 이유: 파일 없이 공유해도 빈 항목이 들어오는 앱이 있다
        const files = form
          .getAll('files')
          .filter((value): value is File => value instanceof File && value.size > 0);

        if (files.length > 0) {
          await stashSharedFiles(files);
          return Response.redirect(
            toAbsolute(`${SHARE_TARGET_PATH}?shared=${SHARE_FILES_FLAG}`, url.origin),
            303,
          );
        }

        const params = new URLSearchParams();
        for (const key of ['title', 'text', 'url'] as const) {
          const value = form.get(key);
          if (typeof value === 'string' && value.trim()) params.set(key, value);
        }
        const query = params.toString();
        return Response.redirect(
          toAbsolute(query ? `${SHARE_TARGET_PATH}?${query}` : SHARE_TARGET_PATH, url.origin),
          303,
        );
      } catch (error) {
        // 본문을 못 읽어도 화면은 띄운다 — 사용자가 "읽지 못했어요"를 보고 다시 시도할 수 있다
        console.error('[sw] share-target POST 처리 실패', error);
        return Response.redirect(toAbsolute(SHARE_TARGET_PATH, url.origin), 303);
      }
    })(),
  );
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
