PWA 아이콘 — **직접 수정하지 말 것**. 생성물이다.

- icon-192.png (192x192, purpose any)
- icon-512.png (512x512, purpose any)
- icon-512-maskable.png (512x512, 풀블리드 + 세이프존 안쪽으로 축소)

소스는 `src/assets/mainIcon.svg` 하나이고, 여기 파일들과 `public/` 의
favicon.svg · favicon.ico · apple-touch-icon.png · og-image.png 까지 전부

    node scripts/generate-icons.mjs

로 함께 생성된다. 로고를 바꿀 땐 `src/assets/mainIcon.svg` 만 교체하고 이 명령을 다시 돌린다.
