import { useEffect } from 'react';

interface ConfirmModalProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  onCancel: () => void;
  onConfirm: () => void;
  /** 취소할 수 있는 상황이 아닐 때(예: 이미 벌어진 일을 알리기만 하는 모달) false로 숨긴다. 기본 true. */
  showCancel?: boolean;
}

const ConfirmModal = ({
  open,
  title,
  description,
  confirmLabel,
  onCancel,
  onConfirm,
  showCancel = true,
}: ConfirmModalProps) => {
  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onCancel, open]);

  if (!open) return null;

  return (
    <div
      role="presentation"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/65 px-4 backdrop-blur-[6px]"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onCancel();
      }}
    >
      <section
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-description"
        className="w-full max-w-[380px] rounded-[20px] border border-border bg-surface px-7 py-[26px] text-center shadow-modal"
      >
        <h2 id="confirm-title" className="mb-2 text-base font-extrabold text-text-1">
          {title}
        </h2>
        <p id="confirm-description" className="mb-5 text-[13px] leading-relaxed text-text-2">
          {description}
        </p>
        <div className="flex justify-center gap-2.5">
          {showCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="rounded-[11px] bg-surface-2 px-5 py-2.5 text-[13.5px] font-bold text-text-1 hover:bg-surface-3"
            >
              취소
            </button>
          )}
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-[11px] bg-[#B3423F] px-5 py-2.5 text-[13.5px] font-bold text-white hover:bg-[#C74E4B]"
          >
            {confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
};

export default ConfirmModal;
