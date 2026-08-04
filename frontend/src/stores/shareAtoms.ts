import { atomWithStorage } from 'jotai/utils';

/**
 * 공유로 들어온 항목을 마지막으로 저장한 곳.
 *
 * 공유 시트에서 들어오는 흐름은 "던져 놓고 나가기"라 매번 위치를 고르게 하면 저장이 느려진다.
 * 크롬 익스텐션도 같은 이유로 기억한다(extension/src/storage/workspaceStorage.ts).
 *
 * localStorage 에 두는 이유: 공유는 앱을 새로 띄우는 진입이라 세션이 매번 끊긴다.
 * getOnInit 은 첫 렌더부터 기억한 값이 보이게 한다 — 없으면 기본값으로 한 프레임 그렸다가
 * 바뀌어서 선택기가 깜빡인다(authAtoms 의 같은 주석 참고).
 *
 * 목록에 없는 id(탈퇴한 워크스페이스 등)일 수 있으므로 **쓰는 쪽에서 반드시 검증**한다.
 */
export const lastShareSpaceIdAtom = atomWithStorage<number | null>(
  'woojuin:lastShareSpaceId',
  null,
  undefined,
  { getOnInit: true },
);
