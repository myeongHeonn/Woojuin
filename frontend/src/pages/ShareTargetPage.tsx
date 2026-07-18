import { useSearchParams } from 'react-router-dom';

/**
 * OS 공유 시트에서 '우주인' 선택 시 진입하는 페이지 (FR-013)
 * - 카톡/크롬/유튜브: url 파라미터로 깔끔하게 들어옴
 * - 인스타/트위터: text에 URL이 섞여 들어옴 → 정규식 추출
 * - 쿠팡/네이버쇼핑: text에 상품명·가격 포함 → 추후 '구매 물품 링크'(FR-024)와 연동
 */
const URL_REGEX = /https?:\/\/[^\s]+/;

export default function ShareTargetPage() {
  const [params] = useSearchParams();
  const rawUrl = params.get('url');
  const text = params.get('text') ?? '';
  const url = rawUrl ?? text.match(URL_REGEX)?.[0] ?? '';

  return (
    <main>
      <h1>저장하기</h1>
      {/* TODO: 워크스페이스 선택 UI (FR-042) + 저장 API 호출 */}
      <p>공유된 URL: {url || '(URL 없음 — 메모로 저장)'}</p>
    </main>
  );
}
