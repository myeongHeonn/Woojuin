import type { ReactNode } from 'react';
import { classNames } from '@/utils/classNames';

interface MenuItemProps {
  onClick: () => void;
  /** 앞 아이콘 */
  icon?: ReactNode;
  /** 파괴적 동작(삭제 등) — 빨강으로 표시 */
  danger?: boolean;
  children: ReactNode;
}

/** 드롭다운 메뉴의 한 줄 — 아이콘 + 라벨. 생김새를 한 곳에 모아 메뉴마다 똑같게 한다. */
const MenuItem = ({ onClick, icon, danger, children }: MenuItemProps) => (
  <button
    type="button"
    role="menuitem"
    onClick={onClick}
    className={classNames(
      'flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px] transition-colors hover:bg-surface-3 [&>svg]:h-4 [&>svg]:w-4',
      danger ? 'text-danger' : 'text-text-1',
    )}
  >
    {icon}
    {children}
  </button>
);

export default MenuItem;
