import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';
import { RouterProvider } from 'react-router-dom';
import { router } from './routes/router';
import { jotaiStore } from './stores/jotaiStore';
import { startThemeRuntime } from './stores/themeRuntime';
import './styles/index.css';

// 첫 적용은 index.html 의 부팅 스크립트가 이미 해 뒀다(깜빡임 방지) — 여기서 다시 거는 건
// 그 값과 스토어를 맞추고, 이후 변화를 이어받기 위해서다.
startThemeRuntime();

// 배포가 새 빌드를 올리면, 열려 있던 탭은 자기(옛) index.html 이 가리키는 옛 해시 청크를
// 계속 요청한다. 파이프라인이 옛 청크를 7일간 남겨 두므로 대부분은 그대로 동작하지만,
// 그보다 오래된 탭이 lazy 페이지로 처음 이동하면 dynamic import 가 실패한다
// ("Failed to fetch dynamically imported module ..." — dev 에서 실측, 2026-07-31).
// Vite 는 이때 vite:preloadError 를 쏘므로 새로고침 한 번으로 새 빌드를 받게 한다.
//
// 시간 가드는 무한 리로드 방지다 — 새로고침해도 같은 청크가 또 없으면(예: 서비스워커가
// 옛 index.html 을 캐시에서 서빙) 리로드를 반복하는 대신 에러를 그대로 드러낸다.
window.addEventListener('vite:preloadError', (event) => {
  const KEY = 'chunk-reload-at';
  const lastReload = Number(sessionStorage.getItem(KEY) ?? 0);
  if (Date.now() - lastReload < 10_000) return;
  sessionStorage.setItem(KEY, String(Date.now()));
  event.preventDefault();
  window.location.reload();
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 저장 항목 상태(PROCESSING → DONE) 폴링은 각 쿼리에서 refetchInterval로 제어
      staleTime: 30_000,
      retry: 1,
      // 탭 복귀 시 stale(>staleTime) 쿼리 재요청 — 기본값이지만 의도임을 명시한다.
      // 개인 스크랩북이라 다른 기기의 변경을 빨리 반영하려고 신선도를 우선한다.
      refetchOnWindowFocus: true,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <JotaiProvider store={jotaiStore}>
      <QueryClientProvider client={queryClient}>
        {/*
          v7_startTransition — 라우터의 화면 전환을 startTransition 으로 감싼다.

          이게 없으면 링크를 누르는 **동기 입력**이 곧바로 lazy 청크를 기다리게 되고, React 가
          "A component suspended while responding to synchronous input" 을 던진다. 실제로
          공유 화면에서 로그인 링크를 누를 때 났다 — 레이아웃(AuthLayout·GuestOnly·Layout)까지
          lazy 라서 거의 모든 이동이 청크를 기다릴 수 있다.

          켜면 그 전환이 transition 이 되어 React 가 이전 화면을 유지한 채 청크를 기다린다
          (그게 에러 메시지가 요구하는 처방이다). v7 에서는 기본값이 되므로 미리 켜는 셈이다.
        */}
        <RouterProvider router={router} future={{ v7_startTransition: true }} />
      </QueryClientProvider>
    </JotaiProvider>
  </React.StrictMode>,
);
