import { atom } from 'jotai';

// 세션마다 다시 발급받으면 되므로 authAtoms.ts와 달리 localStorage에 영속시키지 않는다.
export const fcmTokenAtom = atom<string | null>(null);
