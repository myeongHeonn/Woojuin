/**
 * 에러 화면 버튼 외형 — 404 와 예상치 못한 오류 화면이 공유한다.
 *
 * 컴포넌트로 감싸지 않은 이유: 어떤 자리는 라우팅(`<Link>`)이고 어떤 자리는 동작
 * (`<button onClick>`)이라 태그가 다르다. 껍데기를 하나로 묶으면 as/href 같은 분기가
 * 생기는데, 여기서 필요한 건 같은 **외형**뿐이다.
 */

const BASE =
  'inline-flex h-10 items-center justify-center gap-1.5 rounded-md px-4 text-[13.5px] font-semibold transition-colors cursor-pointer [&>svg]:h-4 [&>svg]:w-4';

/** 주 동작 — 화면마다 하나만 둔다 */
export const errorPrimaryActionClass = `${BASE} bg-accent text-white hover:bg-accent-hover`;

/** 보조 동작 */
export const errorSecondaryActionClass = `${BASE} border border-border bg-sidebar/70 text-text-2 backdrop-blur-md hover:text-text-1`;
