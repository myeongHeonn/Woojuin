import SignupForm from '@/components/domain/auth/SignupForm';
import BrandMark from '@/components/ui/BrandMark';
import BackButton from '@/components/ui/button/BackButton';

/** 회원가입 화면 */
export default function SignupPage() {
  return (
    <div className="relative min-h-dvh bg-space">
      <BackButton className="absolute left-4 top-4" />

      <main className="mx-auto flex min-h-dvh max-w-sm flex-col justify-center gap-6 px-6">
        <BrandMark className="justify-center" />
        <SignupForm />
      </main>
    </div>
  );
}
