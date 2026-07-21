import { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import Dot from '@/components/ui/Dot';

interface NavItemProps {
  icon: ReactNode;
  label: string;
  /** 사이드바 접힘 상태 — 라벨을 숨기고 아이콘만 남긴다 */
  collapsed: boolean;
  /** 지금 보고 있는 항목 — 배경 강조 + 우측 보라 점 */
  active?: boolean;
  /** 보조 항목(＋ New workspace)용 흐린 글자색 */
  muted?: boolean;
  /** 우측 추가 요소 (예: Workspaces 의 ⌃) */
  trailing?: ReactNode;
  /**
   * 이동할 경로. 주면 <a>(Link), 없으면 <button> 으로 렌더한다.
   * 화면 이동은 링크여야 새 탭 열기·가운데 클릭·주소 복사가 동작한다.
   */
  to?: string;
  onClick?: () => void;
}

function NavItem({ icon, label, collapsed, active, muted, trailing, to, onClick }: NavItemProps) {
  const className = classNames(
    'group relative flex items-center w-full rounded-md text-[15px] font-medium cursor-pointer transition-colors',
    collapsed ? 'justify-center gap-0 px-0 py-[11px]' : 'gap-3 px-3.5 py-[11px]',
    active
      ? 'bg-surface-2 text-text-1'
      : classNames(muted ? 'text-text-3' : 'text-text-2', 'hover:bg-surface hover:text-text-1'),
  );

  const inner = (
    <>
      <span className="[&>svg]:w-[17px] [&>svg]:h-[17px] [&>svg]:shrink-0 grid place-items-center">
        {icon}
      </span>

      {!collapsed && <span className="truncate">{label}</span>}
      {!collapsed && trailing}

      {/* 선택 표시 점 — 접힘 시 우상단 (목업 right:12px top:9px) */}
      {active && <Dot glow className={collapsed ? 'absolute right-3 top-[9px]' : 'ml-auto'} />}

      {/* 접힘 상태 호버 툴팁 — 목업 `.app.sb-min .nav:hover::after` */}
      {collapsed && (
        <span className="pointer-events-none absolute left-[calc(100%+12px)] top-1/2 z-[60] hidden -translate-y-1/2 whitespace-nowrap rounded-sm border border-border bg-surface-2 px-[11px] py-1.5 text-xs font-semibold text-text-1 shadow-tooltip group-hover:block">
          {label}
        </span>
      )}
    </>
  );

  const title = collapsed ? label : undefined;

  if (to) {
    return (
      <Link to={to} className={className} title={title} onClick={onClick}>
        {inner}
      </Link>
    );
  }

  return (
    <button type="button" className={className} title={title} onClick={onClick}>
      {inner}
    </button>
  );
}

export default NavItem;
