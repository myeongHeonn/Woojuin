import type { ReactNode } from 'react';
import { classNames } from '@/utils/classNames';

interface FilterChipProps {
  /** 켜짐 — 강조 테두리로 표시 */
  active?: boolean;
  onClick?: () => void;
  /** 라벨 앞 요소 (색 점 등) */
  leading?: ReactNode;
  /** 라벨 뒤 개수 */
  count?: number;
  children: ReactNode;
}

/**
 * 필터 칩 — 알약형 토글 버튼의 순수 디자인.
 *
 * 무엇을 거르는지(카테고리·타입·상태…)는 모른다. 켜짐/꺼짐과 라벨만 받는다.
 * 다중 선택 바에서 각 칩이 독립 토글이므로 aria-pressed 로 상태를 알린다.
 */
const FilterChip = ({ active, onClick, leading, count, children }: FilterChipProps) => (
  <button
    type="button"
    aria-pressed={active}
    onClick={onClick}
    className={classNames(
      'inline-flex h-8 shrink-0 cursor-pointer items-center gap-[7px] rounded-pill border px-[14px] text-[13px] transition-colors',
      active
        ? 'border-accent bg-surface-2 text-text-1'
        : 'border-border text-text-2 hover:text-text-1',
    )}
  >
    {leading}
    {children}
    {count !== undefined && <span className="text-text-3">{count}</span>}
  </button>
);

export default FilterChip;
