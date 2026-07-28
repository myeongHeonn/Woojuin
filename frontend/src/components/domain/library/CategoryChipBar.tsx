import type { ComponentType, SVGProps } from 'react';
import { classNames } from '@/utils/classNames';
import { useHorizontalScroll } from '@/hooks/useHorizontalScroll';
import Dot from '@/components/ui/Dot';
import FilterChip from '@/components/ui/FilterChip';
import { ChevronLeftIcon, ChevronRightIcon, ManageIcon } from '@/assets/icons';

/** 칩 하나가 그리는 데 필요한 것 — 서버 CategoryResponse(id·이름·hex 색) */
export interface CategoryChip {
  categoryId: number;
  name: string;
  /** 카드/별자리 표시용 hex 색 (예 "#C9B8FF") */
  color: string;
}

interface CategoryChipBarProps {
  chips: CategoryChip[];
  /** 선택된 카테고리 id 들. 비어 있으면 [전체]가 켜진 상태 */
  selected: number[];
  /** 카테고리 칩 토글 (다중 선택, 클라이언트 필터) */
  onToggle: (categoryId: number) => void;
  /** [전체] — 선택을 모두 해제한다 */
  onSelectAll: () => void;
  /** 즐겨찾기만 보기 (서버 필터 favorite=true) */
  favoriteActive: boolean;
  onToggleFavorite: () => void;
  /** 카테고리 관리 화면 열기 */
  onManage: () => void;
}

/** 홑화살표 스크롤 버튼 — 끝에 닿으면 비활성 */
const ScrollButton = ({
  icon: Icon,
  label,
  disabled,
  onClick,
}: {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  label: string;
  disabled: boolean;
  onClick: () => void;
}) => (
  <button
    type="button"
    aria-label={label}
    disabled={disabled}
    onClick={onClick}
    className={classNames(
      'grid h-8 w-7 shrink-0 place-items-center rounded-sm text-text-3 transition-colors',
      '[&>svg]:h-[18px] [&>svg]:w-[18px]',
      disabled ? 'cursor-default opacity-30' : 'cursor-pointer hover:text-text-1',
    )}
  >
    <Icon />
  </button>
);

/**
 * 보관함 상단 필터 바.
 *
 * 왼쪽은 고정 — [관리]는 카테고리 CRUD 화면을 열고, [즐겨찾기]는 서버 필터다.
 * 오른쪽은 카테고리 칩들. 개수가 많아 가로 스크롤이며 홑화살표로 넘긴다.
 *
 * 필터가 두 종류다: 즐겨찾기는 서버(queryKey)로, 카테고리 선택은 받은 목록에
 * 얹는 클라이언트 필터다. 그래서 콜백이 서로 다른 상태로 흐른다.
 *
 * 색 점은 서버가 준 hex 색(CategoryResponse.color)을 그대로 쓴다 — 성좌 별자리와 같은 팔레트.
 * 선택 상태를 직접 들지 않는다(controlled) — 필터 주인은 페이지다.
 */
const CategoryChipBar = ({
  chips,
  selected,
  onToggle,
  onSelectAll,
  favoriteActive,
  onToggleFavorite,
  onManage,
}: CategoryChipBarProps) => {
  // 가로 스크롤 동작(끝 감지·이동)은 훅으로 분리 — 이 컴포넌트는 배치·표현만 맡는다.
  // 칩 수가 바뀌면 넘침 여부를 다시 계산하도록 chips.length 를 넘긴다.
  const { scrollRef, atStart, atEnd, onScroll, scrollBy } = useHorizontalScroll(chips.length);

  return (
    <div className="flex items-center gap-2 pt-10 pb-10">
      {/* ── 고정 왼쪽 ─────────────────────────── */}
      <button
        type="button"
        onClick={onManage}
        className="inline-flex h-8 shrink-0 cursor-pointer items-center gap-[7px] rounded-pill border border-border px-[13px] text-[13px] text-text-2 transition-colors hover:text-text-1 [&>svg]:h-4 [&>svg]:w-4"
      >
        <ManageIcon />
        관리
      </button>

      <FilterChip active={favoriteActive} onClick={onToggleFavorite}>
        즐겨찾기
      </FilterChip>

      <div className="h-5 w-px shrink-0 bg-border" />

      {/* ── 카테고리 (가로 스크롤) ──────────────── */}
      <ScrollButton
        icon={ChevronLeftIcon}
        label="이전 카테고리"
        disabled={atStart}
        onClick={() => scrollBy(-220)}
      />

      <div
        ref={scrollRef}
        onScroll={onScroll}
        role="group"
        aria-label="카테고리 필터"
        className="scrollbar-none flex min-w-0 flex-1 items-center gap-2 overflow-x-auto"
      >
        <FilterChip active={selected.length === 0} onClick={onSelectAll}>
          전체
        </FilterChip>

        {chips.map(({ categoryId, name, color }) => (
          <FilterChip
            key={categoryId}
            active={selected.includes(categoryId)}
            leading={<Dot hex={color} />}
            onClick={() => onToggle(categoryId)}
          >
            {name}
          </FilterChip>
        ))}
      </div>

      <ScrollButton
        icon={ChevronRightIcon}
        label="다음 카테고리"
        disabled={atEnd}
        onClick={() => scrollBy(220)}
      />
    </div>
  );
};

export default CategoryChipBar;
