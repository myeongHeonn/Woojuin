import type { ComponentType, SVGProps } from 'react';
import { classNames } from '@/utils/classNames';

interface ScrollButtonProps {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  label: string;
  /** 그 방향 끝에 닿았는가 — useHorizontalScroll 의 atStart/atEnd */
  disabled: boolean;
  onClick: () => void;
}

/**
 * 가로 스크롤 영역의 좌/우 이동 버튼 — 끝에 닿으면 비활성.
 *
 * 스크롤바를 숨긴 영역(scrollbar-none)에는 이런 버튼이 **접근 수단 그 자체**다.
 * 트랙패드·터치는 밀어서 넘길 수 있지만 마우스만 쓰는 데스크톱에는 다른 방법이 없다.
 */
const ScrollButton = ({ icon: Icon, label, disabled, onClick }: ScrollButtonProps) => (
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

export default ScrollButton;
