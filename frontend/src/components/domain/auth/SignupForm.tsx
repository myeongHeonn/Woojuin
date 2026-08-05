import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import axios from 'axios';
import { checkEmailAvailability, signup } from '@/services/auth';
import { signupSchema, type SignupFormValues } from '@/schemas/authSchemas';
import { useDebounce } from '@/hooks/useDebounce';
import FormTextField from '@/components/ui/form/FormTextField';
import SubmitButton from '@/components/ui/button/SubmitButton';
import PrivacyPolicyConsentModal from '@/components/domain/auth/PrivacyPolicyConsentModal';

// 백엔드 SignupService가 중복 이메일일 때 던지는 메시지와 동일해야 한다.
// 코드 없이 메시지 문자열로만 구분 가능한 유일한 signup 실패 사유라 이 값으로 매칭한다.
const EMAIL_TAKEN_MESSAGE = '이미 가입된 이메일입니다';

/** 회원가입 폼 — /signup 페이지에서 쓴다. */
const SignupForm = () => {
  const navigate = useNavigate();
  const formMethods = useForm<SignupFormValues>({ resolver: zodResolver(signupSchema) });
  const { handleSubmit, watch, setError, clearErrors } = formMethods;

  // 체크박스는 직접 체크되지 않는다 — 눌러도 모달이 뜨고, 모달을 끝까지 읽고 확인해야
  // 이 값이 true가 된다(은행 상품 가입 약관 동의 UX).
  const [agreedToPrivacyPolicy, setAgreedToPrivacyPolicy] = useState(false);
  const [privacyModalOpen, setPrivacyModalOpen] = useState(false);

  // 형식이 맞을 때만 서버에 물어본다 — 타이핑 중간값으로 매번 호출하지 않기 위해
  const email = watch('email');
  const debouncedEmail = useDebounce(email, 500);
  const isValidEmailFormat = signupSchema.shape.email.safeParse(debouncedEmail).success;

  const { data: isEmailAvailable } = useQuery({
    queryKey: ['auth', 'check-email', debouncedEmail],
    queryFn: () => checkEmailAvailability(debouncedEmail),
    enabled: isValidEmailFormat,
  });

  useEffect(() => {
    if (!isValidEmailFormat || isEmailAvailable === undefined) return;
    if (isEmailAvailable) {
      clearErrors('email');
    } else {
      setError('email', { type: 'manual', message: EMAIL_TAKEN_MESSAGE });
    }
  }, [isEmailAvailable, isValidEmailFormat, clearErrors, setError]);

  const signupMutation = useMutation({
    mutationFn: signup,
    onSuccess: () => {
      // replace — 가입 완료된 화면으로 되돌아갈 일이 없고, 남겨 두면 로그인 뒤 뒤로가기가
      // GuestOnly 에 되돌려져 "눌러도 아무 일이 없는" 상태가 된다
      navigate('/login', { replace: true });
    },
    onError: (error) => {
      // 이메일 중복은 실시간 체크와 같은 자리(필드 밑)에 표시 — 기존 useEffect가
      // 이메일을 바꾸면 자동으로 지워주므로 배너보다 오해의 소지가 적다.
      if (axios.isAxiosError(error) && error.response?.data?.message === EMAIL_TAKEN_MESSAGE) {
        setError('email', { type: 'manual', message: EMAIL_TAKEN_MESSAGE });
      }
    },
  });

  const errorMessage =
    axios.isAxiosError(signupMutation.error) &&
    signupMutation.error.response?.data?.message !== EMAIL_TAKEN_MESSAGE
      ? (signupMutation.error.response?.data?.message ?? '회원가입에 실패했습니다')
      : null;

  return (
    <div className="flex w-full flex-col gap-4">
      <form
        className="flex flex-col gap-3"
        onSubmit={handleSubmit((values) => signupMutation.mutate(values))}
      >
        <FormTextField type="email" placeholder="이메일" name="email" formMethods={formMethods} />
        <FormTextField
          type="password"
          placeholder="비밀번호"
          name="password"
          formMethods={formMethods}
        />
        <FormTextField type="text" placeholder="닉네임" name="nickname" formMethods={formMethods} />
        {errorMessage && <p className="text-sm text-red-400">{errorMessage}</p>}

        {/* 체크박스는 직접 토글되지 않는다 — 클릭하면 모달이 뜨고, 끝까지 읽고 확인해야 체크된다 */}
        <div className="flex items-center gap-2 text-xs text-text-3">
          <input
            type="checkbox"
            checked={agreedToPrivacyPolicy}
            readOnly
            onClick={(e) => {
              e.preventDefault();
              setPrivacyModalOpen(true);
            }}
            aria-label="개인정보처리방침에 동의"
            className="h-4 w-4 shrink-0 rounded border-border-soft accent-accent"
          />
          <button
            type="button"
            onClick={() => setPrivacyModalOpen(true)}
            className="text-left underline underline-offset-2 hover:text-text-2"
          >
            개인정보처리방침에 동의합니다
          </button>
        </div>

        <SubmitButton
          pending={signupMutation.isPending}
          pendingLabel="가입 중..."
          disabled={!agreedToPrivacyPolicy}
        >
          회원가입
        </SubmitButton>
      </form>

      <PrivacyPolicyConsentModal
        open={privacyModalOpen}
        onConfirm={() => {
          setAgreedToPrivacyPolicy(true);
          setPrivacyModalOpen(false);
        }}
      />

      <p className="text-center text-sm text-text-3">
        이미 계정이 있으신가요? {/* replace — 이유는 LoginForm 의 반대쪽 링크에 적어 뒀다 */}
        <Link to="/login" replace className="font-semibold text-accent hover:text-accent-hover">
          로그인
        </Link>
      </p>
    </div>
  );
};

export default SignupForm;
