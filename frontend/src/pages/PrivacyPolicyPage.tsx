import BackButton from '@/components/ui/button/BackButton';
import BrandMark from '@/components/ui/BrandMark';
import PrivacyPolicyContent from '@/components/domain/auth/PrivacyPolicyContent';

/**
 * 개인정보처리방침 — /privacy (로그인 없이 접근 가능한 공개 문서).
 * 본문은 PrivacyPolicyContent를 공유한다(회원가입 동의 모달과 같은 텍스트).
 */
const PrivacyPolicyPage = () => (
  <div className="min-h-dvh bg-space text-text-1">
    <BackButton className="fixed left-4 top-4" />

    <main className="mx-auto max-w-2xl px-6 pb-24 pt-[calc(64px+var(--safe-top))] desktop:pt-20">
      <BrandMark className="justify-center" />

      <h1 className="mt-10 text-2xl font-extrabold tracking-tight">개인정보처리방침</h1>

      <div className="mt-2">
        <PrivacyPolicyContent />
      </div>
    </main>
  </div>
);

export default PrivacyPolicyPage;
