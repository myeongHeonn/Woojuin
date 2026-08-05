/**
 * 우주인 일러스트(PNG)를 화면 표시 크기에 맞춰 줄이고 WebP 로 굽는다.
 *
 *   node scripts/optimize-spacemans.mjs
 *
 * 왜 필요한가 — 원본이 표시 크기보다 훨씬 크게 들어와 있었다.
 * spaceman_no_bg 는 372x372/151KB 인데 화면에는 112px(h-28)로 그린다.
 * 지도 뷰·보관함 빈 상태에서 매번 그 151KB 를 받는다(2026-08-05 실측: 지도 모바일
 * 전송량 3,059KB 중 151KB).
 *
 * 결과물은 커밋되는 바이너리라, 생성 규칙이 남아 있지 않으면 나중에 같은 걸 다시
 * 만들 수 없다 — generate-icons.mjs 와 같은 이유로 스크립트를 남긴다.
 * 래스터화도 같은 방식(playwright chromium)이라 새 의존성이 없다.
 */
import { chromium } from 'playwright';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, 'src/assets/spacemans');

/**
 * 표시 크기의 2배로 굽는다 — 고밀도 화면(DPR 2)에서도 선명하게.
 * quality 0.85 는 눈으로 차이가 안 나면서 용량이 크게 떨어지는 지점이다.
 */
const TARGETS = [
  // 지도 빈 상태(MapPlacePanel)·보관함 빈 상태(EmptyState) — 최대 h-28(112px)
  { source: 'spaceman_no_bg.png', out: 'spaceman_no_bg.webp', size: 224 },
  // 에러 화면(ErrorScreen). 원본(1254px)에서 바로 줄인다 — 중간본(288px)을 거치면
  // 두 번 리샘플링돼 화질만 손해다.
  { source: 'spaceman-floating-2.png', out: 'spaceman-floating.webp', size: 224 },
];

const QUALITY = 0.85;

const browser = await chromium.launch();
const page = await browser.newPage();

for (const { source, out: outName, size } of TARGETS) {
  const src = path.join(dir, source);
  const png = await readFile(src);
  const dataUri = `data:image/png;base64,${png.toString('base64')}`;

  // 캔버스에 줄여 그린 뒤 WebP 로 인코딩한다(브라우저 인코더라 별도 라이브러리가 없다)
  const base64 = await page.evaluate(
    async ({ dataUri, size, quality }) => {
      const img = new Image();
      img.src = dataUri;
      await img.decode();

      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d');
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, size, size);

      return canvas.toDataURL('image/webp', quality).split(',')[1];
    },
    { dataUri, size, quality: QUALITY },
  );

  const out = path.join(dir, outName);
  const webp = Buffer.from(base64, 'base64');
  await writeFile(out, webp);

  const before = Math.round(png.length / 1024);
  const after = Math.round(webp.length / 1024);
  console.log(
    `${source} → ${outName}  ${before}KB → ${after}KB (-${Math.round((1 - webp.length / png.length) * 100)}%)`,
  );
}

await browser.close();
