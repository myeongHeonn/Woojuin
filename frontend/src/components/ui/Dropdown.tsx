import { useEffect, useRef, useState, type ReactNode } from 'react';
import { classNames } from '@/utils/classNames';

interface DropdownProps {
  /** 여는 버튼 — toggle 을 onClick 에 걸어 렌더한다(open 으로 상태 표시 가능) */
  renderTrigger: (toggle: () => void, open: boolean) => ReactNode;
  /** 패널 내용 — close 를 받아 항목 클릭 후 스스로 닫는다 */
  children: (close: () => void) => ReactNode;
  /** 패널 정렬 (기본 오른쪽 끝 맞춤) */
  align?: 'left' | 'right';
}

/**
 * 트리거 아래로 뜨는 드롭다운 껍데기 — 여닫기·바깥클릭·ESC·위치만 책임진다(SRP).
 * 안의 내용(메뉴·피커)은 children 이 정한다. ⋮ 액션 메뉴, 카테고리 추가 피커가 함께 쓴다.
 * (헤더에 붙는 팝오버는 HeaderPopover, 가운데 모달은 Overlay — 이건 콘텐츠 안 드롭다운)
 */
const Dropdown = ({ renderTrigger, children, align = 'right' }: DropdownProps) => {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const close = () => setOpen(false);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) close();
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return (
    <div ref={wrapRef} className="relative inline-flex">
      {renderTrigger(() => setOpen((v) => !v), open)}

      {open && (
        <div
          role="menu"
          className={classNames(
            'absolute top-[calc(100%+6px)] z-50 min-w-[150px] rounded-lg border border-border bg-surface p-1 shadow-float',
            align === 'right' ? 'right-0' : 'left-0',
          )}
        >
          {children(close)}
        </div>
      )}
    </div>
  );
};

export default Dropdown;
