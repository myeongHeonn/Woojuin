/**
 * 파비콘·PWA 아이콘·OG 이미지를 한 번에 생성한다.
 *
 *   node scripts/generate-icons.mjs
 *
 * 소스는 **src/assets/mainIcon.svg 하나뿐**이다(앱 안 BrandMark 가 쓰는 그 파일).
 * 로고가 바뀌면 그 파일만 교체하고 이 스크립트를 다시 돌리면 전부 따라온다.
 * 결과물은 커밋되는 바이너리라, 생성 규칙이 남아 있지 않으면 나중에 같은 걸 다시 만들 수 없다.
 *
 * 래스터화는 playwright(chromium)로 한다 — 이미 devDependency 라서 새 의존성이 없고,
 * 원본이 쓰는 filter·mix-blend-mode·radialGradient 를 브라우저와 동일하게 렌더한다.
 */
import { chromium } from 'playwright';
import { mkdir, writeFile, readFile, copyFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const publicDir = path.join(root, 'public');
const iconsDir = path.join(publicDir, 'icons');
const sourceSvg = path.join(root, 'src/assets/mainIcon.svg');

/** OG 이미지 배경 — theme.css 의 --color-space / --color-accent */
const SPACE = '#0e1017';
const ACCENT = '#7c6cf0';

const svgText = await readFile(sourceSvg, 'utf8');
const svgDataUri = `data:image/svg+xml;utf8,${encodeURIComponent(svgText)}`;

/**
 * 마스커블 아이콘용 래퍼.
 * 원본은 검은 판을 꽉 채우고 반짝임이 x=55 까지 나가서, 안드로이드 원형 마스크에 잘린다.
 * 그래서 원본을 **손대지 않고** 중앙 기준으로 축소해 세이프존(중앙 80% 원) 안에 넣고,
 * 뒤에 같은 검정을 풀블리드로 깐다. 0.72 에서 반짝임 끝이 중심에서 r≈20 (한계 24).
 */
const maskableSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 60" width="60" height="60">
  <rect width="60" height="60" fill="#000000"/>
  <g transform="translate(30 30) scale(0.72) translate(-30 -30)">
    <image href="${svgDataUri}" x="0" y="0" width="61" height="61"/>
  </g>
</svg>`;

/**
 * 정사각 래퍼. 원본 viewBox 는 61x61 인데 검은 판은 60x60 이라 오른쪽·아래에 1px 투명
 * 띠가 남는다 — 아이콘으로 구우면 그 띠가 그대로 보이므로 60x60 만 잘라 쓴다.
 */
const squareSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 60" width="60" height="60">
  <image href="${svgDataUri}" x="0" y="0" width="61" height="61"/>
</svg>`;

/** 카톡·슬랙 공유 미리보기(1200x630). 폰트는 index.html 과 같은 Pretendard CDN. */
const OG_HTML = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"/>
<link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.css" rel="stylesheet"/>
<style>
  * { margin: 0; box-sizing: border-box; }
  body {
    width: 1200px; height: 630px; display: flex; align-items: center; gap: 64px;
    padding: 0 96px; background: ${SPACE}; color: #f0f2f6; overflow: hidden;
    font-family: Pretendard, 'Malgun Gothic', system-ui, sans-serif;
  }
  /* 성좌 느낌의 은은한 광원 — 단색 배경이 밋밋해서 accent 로 한 겹 깐다 */
  .glow { position: absolute; inset: 0;
    background:
      radial-gradient(620px 420px at 78% 22%, rgba(124,108,240,.28), transparent 70%),
      radial-gradient(420px 300px at 12% 88%, rgba(143,180,255,.14), transparent 70%); }
  /* BrandMark 가 rounded-[9px] 로 깎아 쓰므로 앱에서 보이는 모습과 같게 둔다 */
  .mark { position: relative; width: 200px; height: 200px; flex: none; border-radius: 44px;
    box-shadow: 0 24px 60px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.06); }
  .copy { position: relative; }
  h1 { font-size: 96px; font-weight: 800; letter-spacing: -.03em; line-height: 1.05; }
  p { margin-top: 20px; font-size: 40px; font-weight: 500; color: #b0b6c3; letter-spacing: -.02em; }
  .dot { color: ${ACCENT}; }
</style></head>
<body>
  <div class="glow"></div>
  <img class="mark" src="${svgDataUri}" alt=""/>
  <div class="copy">
    <h1>우주인<span class="dot">.</span></h1>
    <p>저장은 1초, 정리는 AI가, 찾을 땐 검색 한 번</p>
  </div>
</body></html>`;

/** ICO 컨테이너를 직접 조립한다(PNG 페이로드 방식 — 모든 현행 브라우저가 읽는다). */
function buildIco(pngs) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // 1 = icon
  header.writeUInt16LE(pngs.length, 4);

  let offset = 6 + pngs.length * 16;
  const entries = pngs.map(({ size, data }) => {
    const e = Buffer.alloc(16);
    e.writeUInt8(size >= 256 ? 0 : size, 0); // 256 은 0 으로 표기하는 규격
    e.writeUInt8(size >= 256 ? 0 : size, 1);
    e.writeUInt8(0, 2); // 팔레트 색 수(트루컬러라 0)
    e.writeUInt8(0, 3); // reserved
    e.writeUInt16LE(1, 4); // color planes
    e.writeUInt16LE(32, 6); // bpp
    e.writeUInt32LE(data.length, 8);
    e.writeUInt32LE(offset, 12);
    offset += data.length;
    return e;
  });

  return Buffer.concat([header, ...entries, ...pngs.map((p) => p.data)]);
}

const browser = await chromium.launch();

/** SVG 문자열을 정확히 size x size PNG 로 굽는다. */
async function rasterize(svg, size) {
  const page = await browser.newPage({ viewport: { width: size, height: size } });
  await page.setContent(
    `<style>html,body{margin:0;padding:0}svg{display:block;width:${size}px;height:${size}px}</style>${svg}`,
  );
  const buf = await page.screenshot({ omitBackground: true });
  await page.close();
  return buf;
}

await mkdir(iconsDir, { recursive: true });

// ── 1. SVG 파비콘 (탭 아이콘 본체) ─────────────────────────────
// 원본을 그대로 복사한다. SVG 파비콘은 Chrome·Firefox·Edge 가 지원한다.
await copyFile(sourceSvg, path.join(publicDir, 'favicon.svg'));

// ── 2. ICO 폴백 (SVG 파비콘 미지원 브라우저·북마크바) ──────────
const icoPngs = [];
for (const size of [16, 32, 48]) {
  icoPngs.push({ size, data: await rasterize(squareSvg, size) });
}
await writeFile(path.join(publicDir, 'favicon.ico'), buildIco(icoPngs));

// ── 3. PWA 아이콘 ─────────────────────────────────────────────
await writeFile(path.join(iconsDir, 'icon-192.png'), await rasterize(squareSvg, 192));
await writeFile(path.join(iconsDir, 'icon-512.png'), await rasterize(squareSvg, 512));
await writeFile(path.join(iconsDir, 'icon-512-maskable.png'), await rasterize(maskableSvg, 512));

// ── 4. iOS 홈 화면 ────────────────────────────────────────────
// iOS 는 자기가 모서리를 깎으므로 정사각·불투명으로 준다(투명 모서리는 검게 뭉갠다).
await writeFile(path.join(publicDir, 'apple-touch-icon.png'), await rasterize(squareSvg, 180));

// ── 5. OG 이미지 ──────────────────────────────────────────────
const ogPage = await browser.newPage({ viewport: { width: 1200, height: 630 } });
await ogPage.setContent(OG_HTML);
// 웹폰트가 늦게 오면 fallback 으로 구워진다 — 실패해도 진행(시스템 폰트로 렌더).
await ogPage.evaluate(() => document.fonts.ready).catch(() => {});
await ogPage.waitForTimeout(300);
await writeFile(path.join(publicDir, 'og-image.png'), await ogPage.screenshot());
await ogPage.close();

await browser.close();

console.log('생성 완료 (소스: src/assets/mainIcon.svg)');
console.log('  public/favicon.svg          (원본 복사)');
console.log('  public/favicon.ico          (16·32·48)');
console.log('  public/apple-touch-icon.png (180)');
console.log('  public/og-image.png         (1200x630)');
console.log('  public/icons/icon-192.png');
console.log('  public/icons/icon-512.png');
console.log('  public/icons/icon-512-maskable.png');
