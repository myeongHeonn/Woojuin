import { useState } from 'react';
import PrivacyPolicyConsentModal from '@/components/domain/auth/PrivacyPolicyConsentModal';

interface PrivacyPolicyConsentCheckboxProps {
  agreed: boolean;
  onAgree: () => void;
}

/**
 * 개인정보처리방침 동의 체크박스 — 이메일 회원가입 폼과 구글 온보딩 화면이 함께 쓴다.
 * 체크박스는 직접 토글되지 않는다 — 클릭하면 모달이 뜨고, 끝까지 읽고 확인해야 체크된다
 * (은행 상품 가입 약관 동의 UX. 모달 자체의 닫힘 규칙은 PrivacyPolicyConsentModal 참고).
 */
const PrivacyPolicyConsentCheckbox = ({ agreed, onAgree }: PrivacyPolicyConsentCheckboxProps) => {
  const [modalOpen, setModalOpen] = useState(false);

  return (
    <>
      <div className="flex items-center gap-2 text-xs text-text-3">
        <input
          type="checkbox"
          checked={agreed}
          readOnly
          onClick={(e) => {
            e.preventDefault();
            setModalOpen(true);
          }}
          aria-label="개인정보처리방침에 동의"
          className="h-4 w-4 shrink-0 rounded border-border-soft accent-accent"
        />
        <button
          type="button"
          onClick={() => setModalOpen(true)}
          className="text-left underline underline-offset-2 hover:text-text-2"
        >
          개인정보처리방침에 동의합니다
        </button>
      </div>

      <PrivacyPolicyConsentModal
        open={modalOpen}
        onConfirm={() => {
          onAgree();
          setModalOpen(false);
        }}
      />
    </>
  );
};

export default PrivacyPolicyConsentCheckbox;
