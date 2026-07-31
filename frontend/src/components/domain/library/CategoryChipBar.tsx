import type { ReactNode } from 'react';
import { useHorizontalScroll } from '@/hooks/useHorizontalScroll';
import Dot from '@/components/ui/Dot';
import FilterChip from '@/components/ui/FilterChip';
import ScrollButton from '@/components/ui/button/ScrollButton';
import { ChevronLeftIcon, ChevronRightIcon } from '@/assets/icons';

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
  /** [관리] 자리 — 트리거+팝오버가 한 몸이라 버튼 대신 슬롯으로 받는다 */
  manage: ReactNode;
}

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
  manage,
}: CategoryChipBarProps) => {
  // 가로 스크롤 동작(끝 감지·이동)은 훅으로 분리 — 이 컴포넌트는 배치·표현만 맡는다.
  // 칩 수가 바뀌면 넘침 여부를 다시 계산하도록 chips.length 를 넘긴다.
  const { scrollRef, atStart, atEnd, onScroll, scrollBy } = useHorizontalScroll(chips.length);

  return (
    // 모바일은 2줄(고정 컨트롤 / 카테고리 스크롤), 데스크톱은 한 줄로 편다.
    <div className="flex flex-col gap-2 pt-10 pb-10 desktop:flex-row desktop:items-center">
      {/* ── 고정 컨트롤 (관리·즐겨찾기) ─────────── */}
      <div className="flex shrink-0 items-center gap-2">
        {manage}

        <FilterChip active={favoriteActive} onClick={onToggleFavorite}>
          즐겨찾기
        </FilterChip>

        {/* 구분선은 한 줄일 때(데스크톱)만 의미 있다 */}
        <div className="hidden h-5 w-px shrink-0 bg-border desktop:block" />
      </div>

      {/* ── 카테고리 (가로 스크롤) ──────────────── */}
      <div className="flex min-w-0 items-center gap-2 desktop:flex-1">
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
    </div>
  );
};

export default CategoryChipBar;
