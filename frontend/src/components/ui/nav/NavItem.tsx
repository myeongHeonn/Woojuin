import { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import Dot from '@/components/ui/Dot';

interface NavItemProps {
  icon: ReactNode;
  label: string;
  /** 사이드바 접힘 상태 — 라벨을 숨기고 아이콘만 남긴다 */
  collapsed: boolean;
  /** 보조 항목(＋ New workspace)용 흐린 글자색 */
  muted?: boolean;
  /** 우측 추가 요소 (예: Workspaces 의 ⌃) */
  trailing?: ReactNode;
  /**
   * 이동할 경로. 주면 <a>(NavLink), 없으면 <button> 으로 렌더한다.
   * 화면 이동은 링크여야 새 탭 열기·가운데 클릭·주소 복사가 동작한다.
   *
   * 선택 상태(보라 점)는 이 경로와 현재 URL 을 NavLink 가 대조해 스스로 정한다 —
   * 소비처가 useParams 로 따로 계산하지 않는다.
   * 하위 경로까지 선택으로 친다(/workspace/1 은 /workspace/1/map 에서도 켜진다).
   */
  to?: string;
  onClick?: () => void;
  /**
   * 항목 오른쪽에 겹쳐 놓는 조작 요소(⋮ 메뉴 등).
   *
   * `trailing` 과 달리 **링크 바깥**에 그린다. `to` 가 있으면 이 항목은 `<a>` 인데, 그
   * 안에 버튼을 넣으면 잘못된 HTML 이고 누를 때 이동과 메뉴 열기가 동시에 일어난다.
   * 그래서 링크와 형제로 두고 위에 겹친다.
   *
   * 링크에는 오른쪽 여백(pr-10)을 준다 — 라벨이 ⋮ 밑으로 파고들지 않게 자리를 미리 비운다.
   * 호버할 때만 여백을 주면 글자가 다시 잘리며 흔들리고, 여백 없이 겹치면 긴 이름이 버튼
   * 아래로 들어간다. 대신 긴 이름은 그만큼 일찍 잘린다.
   */
  actions?: ReactNode;
}

function NavItem({ icon, label, collapsed, muted, trailing, to, onClick, actions }: NavItemProps) {
  // 접힌 폭에는 아이콘 하나가 겨우 들어가므로 액션은 펼친 상태에서만 그린다.
  // (ReactNode 는 0 일 수도 있어 Boolean 으로 좁힌다 — classNames 는 문자열만 받는다)
  const showActions = Boolean(actions) && !collapsed;

  const buildClassName = (active: boolean) =>
    classNames(
      'group relative flex items-center w-full rounded-md text-[15px] font-medium cursor-pointer transition-colors',
      collapsed ? 'justify-center gap-0 px-0 py-[11px]' : 'gap-3 px-3.5 py-[11px]',
      active
        ? 'bg-surface-2 text-text-1'
        : classNames(muted ? 'text-text-3' : 'text-text-2', 'hover:bg-surface hover:text-text-1'),
    );

  const inner = (active: boolean) => (
    <>
      <span className="[&>svg]:w-[17px] [&>svg]:h-[17px] [&>svg]:shrink-0 grid place-items-center">
        {icon}
      </span>

      {!collapsed && <span className="truncate">{label}</span>}
      {!collapsed && trailing}

      {/*
        선택 표시 점 — **접힌 상태에서만** 그린다(목업 right:12px top:9px).
        접히면 라벨이 없어 글자색 단서가 사라지고 배경 하나만 남으므로 점이 일을 한다.

        펼친 상태에서는 뺐다. 선택은 이미 배경(#171923 → #2A2E3A)과 글자색(text-2 → text-1)
        두 가지로 드러나고, 의미상으로는 NavLink 가 붙이는 aria-current="page" 가 담당한다.
        게다가 점의 제 자리는 오른쪽 끝(ml-auto)인데 ⋮ 를 놓으려면 그 자리를 비켜야 해서,
        어디에 두든 빈 공간에 떠 있는 모양이 됐다. 자리가 없는 표시는 빼는 쪽이 낫다.
      */}
      {active && collapsed && <Dot glow className="absolute right-3 top-[9px]" />}

      {/* 접힘 상태 호버 툴팁 — 목업 `.app.sb-min .nav:hover::after` */}
      {collapsed && (
        <span className="pointer-events-none absolute left-[calc(100%+12px)] top-1/2 z-[60] hidden -translate-y-1/2 whitespace-nowrap rounded-sm border border-border bg-surface-2 px-[11px] py-1.5 text-xs font-semibold text-text-1 shadow-tooltip group-hover:block">
          {label}
        </span>
      )}
    </>
  );

  const title = collapsed ? label : undefined;

  const item = to ? (
    <NavLink
      to={to}
      className={({ isActive }) => buildClassName(isActive)}
      title={title}
      onClick={onClick}
    >
      {({ isActive }) => inner(isActive)}
    </NavLink>
  ) : (
    <button type="button" className={buildClassName(false)} title={title} onClick={onClick}>
      {inner(false)}
    </button>
  );

  if (!showActions) return item;

  /*
   * actions 가 있을 때만 상자로 감싼다 — 없으면 지금까지와 똑같은 DOM 이라
   * 기존 사용처(FixedNav 등)의 레이아웃이 변하지 않는다.
   *
   * 라벨과 선택 점이 ⋮ 밑으로 파고들지 않게 오른쪽 여백을 링크에 직접 준다(pr-10).
   * 접힌 사이드바에서는 자리가 없어 아예 그리지 않는다.
   */
  return (
    <div className="group/nav-item relative [&>a]:pr-10 [&>button]:pr-10">
      {item}
      {/*
        세로 정렬을 transform(-translate-y-1/2) 대신 flex 로 한다 — transform 은 스태킹
        컨텍스트를 만들고, 그러면 안에서 열리는 드롭다운의 z-index 가 이 상자 안에 갇혀
        아래쪽 항목들에 덮인다(실제로 메뉴를 클릭할 수 없었다).
      */}
      <span className="absolute inset-y-0 right-1.5 flex items-center">{actions}</span>
    </div>
  );
}

export default NavItem;
