import { useRef, useState } from 'react';
import PrivacyPolicyContent from '@/components/domain/auth/PrivacyPolicyContent';
import { classNames } from '@/utils/classNames';

interface PrivacyPolicyConsentModalProps {
  open: boolean;
  onConfirm: () => void;
}

/** 스크롤 끝 판정 여유 — 하위 픽셀 반올림 오차로 끝까지 내렸는데도 안 잡히는 걸 막는다 */
const SCROLL_BOTTOM_THRESHOLD_PX = 4;

/**
 * 회원가입 개인정보처리방침 동의 모달 — 은행 상품 가입 약관 동의 UX를 따른다.
 * 끝까지 스크롤하기 전에는 확인 버튼이 비활성이고, 배경 클릭·ESC로 닫히지 않는다
 * (ConfirmModal과 달리 onCancel이 아예 없다) — 확인 버튼을 눌러야만 닫힌다.
 */
const PrivacyPolicyConsentModal = ({ open, onConfirm }: PrivacyPolicyConsentModalProps) => {
  const [scrolledToBottom, setScrolledToBottom] = useState(false);
  const bodyRef = useRef<HTMLDivElement>(null);

  if (!open) return null;

  const handleScroll = () => {
    const el = bodyRef.current;
    if (!el) return;
    // 한 번 끝까지 내리면 계속 활성 — 다시 위로 스크롤해도 잠기지 않는다(다 읽었다는 사실은 변하지 않음)
    if (el.scrollHeight - el.scrollTop - el.clientHeight <= SCROLL_BOTTOM_THRESHOLD_PX) {
      setScrolledToBottom(true);
    }
  };

  const handleConfirm = () => {
    if (!scrolledToBottom) return;
    setScrolledToBottom(false); // 다음에 다시 열릴 때를 위해 리셋
    onConfirm();
  };

  return (
    <div
      role="presentation"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/65 px-4 backdrop-blur-[6px]"
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="privacy-consent-title"
        className="flex max-h-[80vh] w-full max-w-lg flex-col rounded-[20px] border border-border bg-surface shadow-modal"
      >
        <h2
          id="privacy-consent-title"
          className="px-7 pb-2 pt-6 text-base font-extrabold text-text-1"
        >
          개인정보처리방침
        </h2>

        <div
          ref={bodyRef}
          onScroll={handleScroll}
          data-testid="privacy-policy-scroll-body"
          className="min-h-0 flex-1 overflow-y-auto px-7 text-[13px] leading-relaxed text-text-2"
        >
          <PrivacyPolicyContent />
          {/* 마지막 문단 바로 아래가 스크롤 끝이면 threshold 오차로 안 잡힐 수 있어 여유를 둔다 */}
          <div className="h-4" />
        </div>

        <div className="px-7 pb-6 pt-4">
          <button
            type="button"
            onClick={handleConfirm}
            disabled={!scrolledToBottom}
            className={classNames(
              'w-full rounded-[11px] px-5 py-2.5 text-[13.5px] font-bold text-white transition-colors',
              scrolledToBottom
                ? 'bg-accent hover:bg-accent-hover'
                : 'cursor-not-allowed bg-accent/40',
            )}
          >
            {scrolledToBottom ? '확인' : '끝까지 읽어주세요'}
          </button>
        </div>
      </section>
    </div>
  );
};

export default PrivacyPolicyConsentModal;
