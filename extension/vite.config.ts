import { defineConfig, Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * 그 빌드가 실제로 호출하는 호스트만 manifest 에 남긴다.
 *
 * manifest.json 은 정적 파일이라 모드에 따라 갈릴 수 없다. 그래서 세 환경의 호스트를 모두
 * 적어 두는데, 배포판에 개발 주소(localhost·dev)가 남으면 웹스토어 심사에서 "왜 필요한가"를
 * 되묻는다 — 호스트 권한은 이미 상세 검토 대상이라 게시가 지연된다. 실제 호출 대상은
 * client.ts 가 모드별로 하나씩만 쓰므로(API·웹앱 각 1개), 나머지는 지워도 기능이 같다.
 */
const HOSTS_BY_MODE: Record<string, string[]> = {
  development: ['http://localhost:8080/*', 'http://localhost:5173/*'],
  demo: ['https://api.dev.woojuin.store/*', 'https://dev.woojuin.store/*'],
  production: ['https://api.woojuin.store/*', 'https://woojuin.store/*'],
};

function narrowHostPermissions(mode: string): Plugin {
  return {
    name: 'woojuin:narrow-host-permissions',
    apply: 'build',
    // manifest 는 public/ 복사물이라 번들이 다 쓰인 뒤에 손대야 한다
    writeBundle(options) {
      const allowed = HOSTS_BY_MODE[mode];
      if (!allowed) {
        this.warn(`알 수 없는 모드(${mode}) — host_permissions 를 그대로 둔다`);
        return;
      }
      const target = resolve(options.dir ?? 'dist', 'manifest.json');
      const manifest = JSON.parse(readFileSync(target, 'utf-8')) as { host_permissions?: string[] };
      if (!manifest.host_permissions) return;
      manifest.host_permissions = manifest.host_permissions.filter((o) => allowed.includes(o));
      writeFileSync(target, `${JSON.stringify(manifest, null, 2)}\n`);
      this.info(`host_permissions → ${manifest.host_permissions.join(', ')} (mode=${mode})`);
    },
  };
}

// MV3: popup(HTML) + background(서비스워커) 두 엔트리
export default defineConfig(({ mode }) => ({
  plugins: [react(), narrowHostPermissions(mode)],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      input: {
        popup: resolve(__dirname, 'popup.html'),
        background: resolve(__dirname, 'src/background/index.ts'),
      },
      output: {
        entryFileNames: (chunk) =>
          chunk.name === 'background' ? 'background.js' : 'assets/[name]-[hash].js',
      },
    },
  },
}));
