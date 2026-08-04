import { defineConfig, type Plugin } from 'vite';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';
import { readFile } from 'node:fs/promises';

// maplibre-gl v6 은 지도 타일 파싱을 Web Worker 로 넘기는데, 그 워커가 다시
// `./maplibre-gl-shared.mjs`(477KB) 를 import 한다. Vite 는 워커를
// `new URL('./maplibre-gl-worker.mjs', import.meta.url)` 패턴으로 인식해 **파일만 복사**하고
// 복사한 파일 내부의 import 는 따라가지 않아서 shared 파일이 dist 에 빠진다.
// 결과: dev 서버는 node_modules 를 직접 서빙하므로 정상이고, **프로덕션 빌드에서만 지도가 죽는다**
// (없는 파일 → SPA 폴백 index.html → module MIME 검사 실패 → 워커 생성 불가).
// 2026-07-29 운영 배포에서 발견. 워커 옆에 같은 이름으로 함께 내보내 해결한다.
function maplibreWorkerSharedChunk(): Plugin {
  let assetsDir = 'assets';
  return {
    name: 'maplibre-worker-shared-chunk',
    apply: 'build',
    configResolved(config) {
      assetsDir = config.build.assetsDir;
    },
    async generateBundle() {
      const spec = 'maplibre-gl/dist/maplibre-gl-shared.mjs';
      const resolved = await this.resolve(spec);
      // 파일명이 바뀌면 조용히 깨지는 대신 빌드를 세운다 (maplibre 업그레이드 시 감지용)
      if (!resolved) {
        throw new Error(
          `[maplibre] ${spec} 를 찾지 못했다. maplibre-gl 업그레이드로 워커/공유 파일 구성이 ` +
            `바뀌었는지 확인하고 이 플러그인을 갱신할 것 (frontend/vite.config.ts)`,
        );
      }
      this.emitFile({
        type: 'asset',
        // 워커가 상대경로로 찾으므로 해시 없이 정확히 이 이름이어야 한다
        fileName: `${assetsDir}/maplibre-gl-shared.mjs`,
        source: await readFile(resolved.id),
      });
    },
  };
}

// PWA: injectManifest 전략 사용
// 이유: Web Share Target(공유하기 연동) 처리 + FCM 백그라운드 수신 등
//       커스텀 서비스워커 로직이 필요함 (src/sw.ts)
export default defineConfig({
  // .env.example처럼 프론트/백엔드가 레포 루트 .env 하나를 같이 쓰는 구조라서,
  // Vite 기본값(frontend/.env)이 아니라 루트를 보게 한다. 안 그러면
  // VITE_API_BASE_URL 등이 매번 undefined로 읽힌다.
  envDir: path.resolve(__dirname, '..'),
  plugins: [
    react(),
    tailwindcss(),
    maplibreWorkerSharedChunk(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'prompt',
      devOptions: { enabled: true, type: 'module' },
      injectManifest: {
        // HEIC 디코더 청크(3MB)는 프리캐시에서 뺀다. 넣으면 앱을 설치하기만 해도 초기
        // 다운로드가 몇 배로 뛰는데, 정작 HEIC 를 올리는 순간에만 필요한 코드다.
        // (워크박스 기본 크기 제한에도 걸려 어차피 빠지지만, 그러면 빌드마다 경고만 남는다.)
        // 첫 방문에는 앱 셸과 랜딩만 캐시한다. 로그인 뒤에만 쓰는 우주뷰·지도·사진 변환
        // 청크는 각 화면에 들어갈 때 내려받아 모바일 랜딩의 초기 네트워크 경쟁을 막는다.
        globPatterns: [
          'index.html',
          'registerSW.js',
          'manifest.webmanifest',
          'icons/*.png',
          'assets/index-*.{js,css}',
          'assets/LandingPage-*.{js,css}',
        ],
      },
      manifest: {
        name: '우주인 — 올인원 AI 스크랩북',
        short_name: '우주인',
        description: '저장은 1초, 정리는 AI가, 찾을 땐 검색 한 번',
        lang: 'ko',
        start_url: '/',
        display: 'standalone',
        // theme.css 의 --color-space 와 같은 값. index.html 의 theme-color 와도 맞춰야
        // 설치형 PWA 스플래시/상단바가 앱 배경과 이어져 보인다.
        background_color: '#0e1017',
        theme_color: '#0e1017',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          {
            src: '/icons/icon-512-maskable.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
        // Web Share Target (FR-013): Android에서 OS 공유 시트에 '우주인' 노출
        // PWA를 홈 화면에 설치해야 WebAPK로 등록됨. iOS 미지원(클립보드 감지로 보완)
        share_target: {
          action: '/share-target',
          method: 'GET',
          params: { title: 'title', text: 'text', url: 'url' },
        },
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 무거운 라이브러리를 별도 청크로 분리한다.
        // (1) 성좌·지도에 들어가야만 받아지고 (2) 잘 안 바뀌어 브라우저 캐시가 오래 유지된다.
        //
        // 🔴 HEIC 3종(heic-to·exifr·piexifjs)은 **여기 넣지 말 것.**
        //    `heic: ['heic-to', 'exifr', 'piexifjs']` 로 묶었더니 롤업이 그 청크를 엔트리의
        //    **정적** 의존으로 끌어올려, 빌드된 index.html 에
        //    `<link rel="modulepreload" href="/assets/heic-*.js">` 가 박히고 엔트리 코드에도
        //    `import{...}from"./heic-*.js"` 가 생겼다. 소스가 전부 동적 import 여도 소용없고
        //    서비스워커 globIgnores 로도 못 막는다(프리캐시가 아니라 HTML preload 라서).
        //    결과: HEIC 를 쓸 일 없는 로그인 화면부터 3MB 를 받았다.
        //    실측(2026-08-04, dev): 로그인 전송량 6.1MB · 모바일 FCP 20초 · LCP 21.5초.
        //    빼고 나서 초기 로드 3,562KB → 537KB. 롤업이 동적 import 지점을 보고 알아서
        //    쪼개게 두면 된다(heic-to·exifr·piexifjs 가 각각 지연 청크가 된다).
        //    three·maplibre 는 lazy 라우트 안에서만 쓰여 이 문제가 없다.
        manualChunks: {
          three: ['three'],
          maplibre: ['maplibre-gl'],
          // HEIC 변환에 쓰는 셋(디코더는 libheif wasm 을 품고 있어 3MB). 셋 다 같은 흐름에서
          // 연달아 쓰이므로 한 청크로 묶어 요청을 한 번에 끝낸다. 전부 동적 import 라 초기
          // 번들엔 안 들어가고, 사진 탭에서 HEIC 를 고른 사용자만 받는다. 이름을 고정해 두는
          // 건 아래 injectManifest.globIgnores 가 이 청크를 지목할 수 있게 하려는 것이다.
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 브라우저의 localhost:517x Origin을 그대로 넘기면 Spring CORS가 프록시 요청도
        // 교차 출처 요청으로 판단한다. 브라우저 입장에서는 Vite와 같은 출처로 요청하므로
        // 개발 프록시가 Origin을 제거하고 백엔드에는 일반 서버 간 요청으로 전달한다.
        configure(proxy) {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
});
