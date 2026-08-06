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
 * 정사각 박스·둥근 모서리·배경·hover 를 여기서 고정해 모든 아이콘 버튼이 똑같이 생기게 한다.
 *
 * 배경이 bg-black/60 이었다. 다크 전용 목업을 그대로 옮긴 값이라 라이트에서는 **어두운 상자에
 * 어두운 글자**가 얹혀 닫기 버튼이 보이지 않았다(글자는 text-1 이라 라이트에서 짙어진다).
 * 표면 토큰으로 바꾸면 두 테마 모두 "표면보다 한 단 올라온 칩"이 된다 — 다크에서는 밝은 쪽으로,
 * 라이트에서는 어두운 쪽으로 자동으로 갈린다.
 */
const IconButton = ({ onClick, label, className, children }: IconButtonProps) => (
  <button
    type="button"
    aria-label={label}
    onClick={onClick}
    className={classNames(
      'grid h-8 w-8 place-items-center rounded-[9px] bg-surface-2 text-[15px] text-text-1 hover:bg-surface-3',
      className,
    )}
  >
    {children}
  </button>
);

export default IconButton;
