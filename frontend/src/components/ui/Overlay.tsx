import { useEffect, type ReactNode } from 'react';
import { classNames } from '@/utils/classNames';

interface OverlayProps {
  onClose: () => void;
  /** 카드 추가 클래스 — 너비·패딩·max-h 등 모달마다 다른 부분만 준다 */
  cardClassName?: string;
  children: ReactNode;
}

/**
 * 모달 껍데기 — "가운데 뜬 카드 + 배경/Esc 로 닫힘"만 책임진다(SRP).
 * 안에 무엇을 담는지(제목·이미지·폼)는 모른다. 배경 클릭·Esc 로 닫고,
 * 카드 안쪽 클릭은 stopPropagation 으로 새어나가지 않게 한다.
 * 카드 공통 생김새(radius·surface·shadow)는 여기서 고정하고 나머지는 cardClassName 으로.
 */
const Overlay = ({ onClose, cardClassName, children }: OverlayProps) => {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/65 p-4 backdrop-blur-md"
      onClick={onClose}
    >
      <div
        className={classNames('relative rounded-xl bg-surface shadow-modal', cardClassName)}
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
};

export default Overlay;
