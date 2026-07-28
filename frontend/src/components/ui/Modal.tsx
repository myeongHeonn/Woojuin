import { ReactNode } from 'react';
import Overlay from './Overlay';
import CloseButton from './CloseButton';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

/**
 * 제목 헤더가 있는 단순 모달 — 도메인을 모르는 디자인 시스템 조각.
 * 껍데기(배경/Esc 닫기·가운데 카드)는 Overlay 가, 여기선 제목 헤더 + 내용만 얹는다.
 */
const Modal = ({ open, onClose, title, children }: ModalProps) => {
  if (!open) return null;

  return (
    <Overlay onClose={onClose} cardClassName="w-full max-w-sm p-5">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-base font-bold text-text-1">{title}</h2>
        <CloseButton onClick={onClose} />
      </div>
      {children}
    </Overlay>
  );
};

export default Modal;
