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
        manualChunks: {
          three: ['three'],
          maplibre: ['maplibre-gl'],
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
