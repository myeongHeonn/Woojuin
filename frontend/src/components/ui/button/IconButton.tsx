import type { ReactNode } from 'react';
import { classNames } from '@/utils/classNames';

interface IconButtonProps {
  onClick: () => void;
  /** 접근성 라벨 (aria-label) — 아이콘만 있으니 필수 */
  label: string;
  /** 배치용 (absolute …) — 생김새는 고정, 위치만 바깥에서 준다 */
  className?: string;
  /** 아이콘 또는 글리프(✕ 등) */
  children: ReactNode;
}

/**
 * 아이콘 버튼 틀 — 목업 .modal .x 의 껍데기. 닫기(✕)·다운로드 등이 같이 쓴다.
 * 정사각 박스·둥근 모서리·어두운 배경·hover 를 여기서 고정해 모든 아이콘 버튼이 똑같이 생기게 한다.
 */
const IconButton = ({ onClick, label, className, children }: IconButtonProps) => (
  <button
    type="button"
    aria-label={label}
    onClick={onClick}
    className={classNames(
      'grid h-8 w-8 place-items-center rounded-[9px] bg-black/60 text-[15px] text-text-1 hover:bg-surface-3',
      className,
    )}
  >
    {children}
  </button>
);

export default IconButton;
