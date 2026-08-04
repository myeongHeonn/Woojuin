import { atom } from 'jotai';

export type TutorialReplayType = 'PERSONAL' | 'SHARED_WORKSPACE';

export interface TutorialReplayRequest {
  type: TutorialReplayType;
  workspaceId: number;
  requestId: number;
}

/** DB의 최초 튜토리얼 완료 상태를 바꾸지 않는, 현재 세션의 다시 보기 요청이다. */
export const tutorialReplayAtom = atom<TutorialReplayRequest | null>(null);

/** 우주뷰가 튜토리얼 예시를 보여줘야 하는 동안만 true다. */
export const tutorialActiveAtom = atom(false);

/** 현재 우주뷰가 실제 데이터 대신 튜토리얼 예시 데이터를 표시하는지 나타낸다. */
export const tutorialFixtureVisibleAtom = atom(false);
