import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import axios from 'axios';
import { signup } from '@/services/auth';
import { signupSchema, type SignupFormValues } from '@/schemas/authSchemas';
import FormTextField from '@/components/ui/FormTextField';
import SubmitButton from '@/components/ui/SubmitButton';

/** 회원가입 폼 — /signup 페이지에서 쓴다. */
const SignupForm = () => {
  const navigate = useNavigate();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignupFormValues>({ resolver: zodResolver(signupSchema) });

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
        <FormTextField
          type="email"
          placeholder="이메일"
          name="email"
          register={register}
          error={errors.email}
        />
        <FormTextField
          type="password"
          placeholder="비밀번호"
          name="password"
          register={register}
          error={errors.password}
        />
        <FormTextField
          type="text"
          placeholder="닉네임"
          name="nickname"
          register={register}
          error={errors.nickname}
        />
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
