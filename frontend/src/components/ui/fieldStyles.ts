/**
 * 입력 필드 공통 외형 — TextInput·TextArea 가 공유한다.
 * 도메인 코드는 이 문자열 대신 두 컴포넌트를 쓴다.
 *
 * `pointer-coarse:text-base` 는 터치 기기에서 글자를 16px 로 올린다. iOS Safari 는
 * font-size 가 16px 미만인 입력에 포커스가 가면 페이지를 강제로 확대하고, 키보드가 닫힌
 * 뒤에도 그 배율을 되돌리지 않는다(PWA 는 배율을 되돌릴 브라우저 UI 조차 없다).
 * 화면 너비로 가르면 안 된다 — --breakpoint-desktop 이 640px 이라 폰을 가로로 돌리면
 * 그 위로 올라가 확대가 되살아난다. 문제는 화면 크기가 아니라 입력 방식이다.
 */
export const fieldClass =
  'rounded-md border border-border bg-surface-2 px-3 py-2 text-sm pointer-coarse:text-base text-text-1 outline-none focus:border-accent';
