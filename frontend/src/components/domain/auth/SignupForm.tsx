import { useEffect } from 'react';
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

/** 회원가입 폼 — /signup 페이지에서 쓴다. */
const SignupForm = () => {
  const navigate = useNavigate();
  const formMethods = useForm<SignupFormValues>({ resolver: zodResolver(signupSchema) });
  const { handleSubmit, watch, setError, clearErrors } = formMethods;

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
      setError('email', { type: 'manual', message: '이미 가입된 이메일입니다' });
    }
  }, [isEmailAvailable, isValidEmailFormat, clearErrors, setError]);

  const signupMutation = useMutation({
    mutationFn: signup,
    onSuccess: () => {
      navigate('/login');
    },
  });

  const errorMessage = axios.isAxiosError(signupMutation.error)
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
        <SubmitButton pending={signupMutation.isPending} pendingLabel="가입 중...">
          회원가입
        </SubmitButton>
      </form>

      <p className="text-center text-sm text-text-3">
        이미 계정이 있으신가요?{' '}
        <Link to="/login" className="font-semibold text-accent hover:text-accent-hover">
          로그인
        </Link>
      </p>
    </div>
  );
};

export default SignupForm;
