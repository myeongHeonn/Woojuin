import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { playwright } from '@vitest/browser-playwright';
import path from 'node:path';

/**
 * 테스트는 실제 Chromium 에서 돌린다(browser mode).
 * jsdom 은 CSS 레이아웃 엔진이 없어 getBoundingClientRect 가 전부 0 이라
 * "접으면 72px", "하위 항목이 4px 들여쓰기" 같은 검증을 할 수 없다.
 *
 * PWA 플러그인은 테스트에 불필요하므로 vite.config.ts 를 상속하지 않고 별도 설정한다.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  // 무거운 의존성을 **미리** 최적화해 둔다.
  //
  // 안 해두면 테스트가 도는 중간에 Vite 가 이들을 최적화하면서 페이지를 리로드하고,
  // vitest 가 직접 경고를 낸다:
  //   [vite] (client) optimized dependencies changed. reloading
  //   [vitest] Vite unexpectedly reloaded a test. This may cause tests to fail,
  //            lead to flaky behaviour or duplicated test runs.
  //
  // browser mode 에서는 이때 브라우저 컨텍스트가 어긋나 **테스트가 다 통과했는데도
  // 프로세스가 종료되지 않는다**(CI 에서 13분 넘게 매달려 있는 것을 실제로 관측).
  // 테스트 파일이 늘어날수록 타이밍상 더 잘 재현된다.
  //
  // 🔴 팀 규칙: **런타임 라이브러리를 새로 추가하면 이 목록에도 추가할 것.**
  //    빼먹으면 CI 프론트 테스트가 리로드 경고와 함께 매달렸다가 10분 타임아웃으로 실패한다
  //    (react-hook-form/zod 추가 때 실제 발생). 로그에서 아래 줄의 패키지명을 그대로 옮기면 된다:
  //      [vite] (client) dependencies optimized: <여기 나온 것들>
  optimizeDeps: {
    include: [
      'maplibre-gl',
      'react-dom/client',
      'three',
      'react-hook-form',
      'zod',
      '@hookform/resolvers/zod',
    ],
  },
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
    browser: {
      enabled: true,
      provider: playwright(),
      headless: true,
      // 🔴 테스트 파일을 **순차 실행**한다 (2026-07-31 CI 플레이키의 근본 원인 제거).
      //
      // browser mode 의 모듈 mock(`vi.mock`)은 Vite 서버 쪽에서 모듈 요청을 가로채는
      // 방식이라, 같은 모듈 경로를 여러 파일이 **동시에** mock 하면 어느 등록이 적용될지가
      // 실행마다 달라진다. 실제로 `universe.test.ts` 가 `get.mockReset is not a function`
      // 으로 터졌다 — 그 실행에서는 mock 이 아니라 진짜 axios 인스턴스가 들어왔다는 뜻이고,
      // 같은 커밋을 다시 돌리면 통과했다.
      //
      // 느려질 것을 예상했는데 **반대로 빨라졌다** (로컬 실측, 같은 커밋):
      //   병렬: 397초 / 수집 개수가 실행마다 123·153·156 으로 흔들림 / 매번 다른 파일 실패
      //   순차:  17초 / 3회 연속 36파일 178테스트 전부 통과
      // 브라우저 하나에 여러 iframe 이 동시에 붙어 Vite 서버와 경쟁하던 것이 오히려
      // 비용이었던 셈이다. 결정성과 속도를 같이 얻었으니 되돌릴 이유가 없다.
      fileParallelism: false,
      // 기본은 데스크톱(>= lg). 사이드바 300px·팝오버 앵커처럼 lg 이상을 전제한 검증이 많다.
      // 모바일 동작은 각 테스트에서 page.viewport() 로 줄여서 확인한다.
      viewport: { width: 1280, height: 800 },
      instances: [
        {
          browser: 'chromium',
          // 기본값인 chrome-headless-shell 대신 일반 Chromium 을 쓴다.
          // headless shell 은 서명이 낯설어 보안 프로그램에 차단되는 경우가 있고,
          // 그때 Playwright 가 "Executable doesn't exist" 로 잘못 보고한다.
          launch: { channel: 'chromium' },
        },
      ],
    },
  },
});
