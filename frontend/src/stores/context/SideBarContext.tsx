import { createContext, useContext } from 'react';

/** 선택 항목 id — 고정 항목은 문자열, 워크스페이스는 number 라 서로 섞이지 않는다 */
export type ActiveId = 'personal' | 'trash' | number;

interface SideBarContextValue {
  /** 사이드바가 접혀(닫혀) 있는지 */
  sideBarClosed: boolean;
  toggleSideBar: () => void;
  activeId: ActiveId;
  setActiveId: (id: ActiveId) => void;
}

const SideBarContext = createContext<SideBarContextValue | null>(null);

export const SideBarProvider = SideBarContext.Provider;

/**
 * 사이드바 UI 상태.
 * 사이드바 밖에서는 의미가 없는 상태라 전역 스토어 대신 Context 로 범위를 가둔다.
 */
export const useSideBar = () => {
  const ctx = useContext(SideBarContext);
  if (!ctx) throw new Error('useSideBar 는 <SideBar> 안에서만 사용할 수 있습니다.');
  return ctx;
};
