/** 공유로 들어온 내용을 무엇으로 저장할지 */
export type SharedContent =
  { kind: 'url'; url: string } | { kind: 'memo'; content: string } | { kind: 'empty' };

const URL_REGEX = /https?:\/\/[^\s]+/;

/**
 * OS 공유 시트가 넘겨준 파라미터를 저장할 형태로 정리한다.
 *
 * 앱마다 같은 "링크 공유"를 다르게 보낸다 — manifest 의 share_target.params 가 title·text·url
 * 셋을 받게 돼 있는 건 그래서다.
 *
 *   카톡·크롬·유튜브   url 파라미터로 깔끔하게
 *   인스타·트위터      text 안에 URL 이 문장과 섞여서
 *   쿠팡·네이버쇼핑    text 에 상품명·가격이 붙어서
 *
 * URL 을 찾으면 URL 만 저장한다. 함께 온 상품명·가격 같은 텍스트를 따로 남기지 않는 이유는
 * **서버가 그 페이지를 크롤해 제목·요약을 직접 뽑기 때문**이다(그게 "정리는 AI가" 다).
 * 공유 텍스트를 메모로 덧붙이면 같은 내용이 두 벌 남는다.
 *
 * URL 이 없으면 텍스트를 메모로 저장한다 — 제목만 보내는 앱도 있어 title 까지 합쳐서 본다.
 *
 * 순수 함수로 떼어 둔 이유: 공유는 실기기에서만 재현되는 진입이라, 앱별 형태를 화면 없이
 * 테스트로 고정해 두는 게 유일한 방어선이다.
 */
export const parseSharedContent = (params: {
  url?: string | null;
  text?: string | null;
  title?: string | null;
}): SharedContent => {
  const url = params.url?.trim() ?? '';
  const text = params.text?.trim() ?? '';
  const title = params.title?.trim() ?? '';

  if (url) return { kind: 'url', url };

  const matched = text.match(URL_REGEX)?.[0];
  if (matched) return { kind: 'url', url: matched };

  const memo = [title, text].filter(Boolean).join('\n').trim();
  return memo ? { kind: 'memo', content: memo } : { kind: 'empty' };
};
