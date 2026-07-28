import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { signup } from '@/services/auth';
import { signupSchema, toFieldErrors } from '@/schemas/authSchemas';
import BrandMark from '@/components/ui/BrandMark';
import BackButton from '@/components/ui/BackButton';
import TextInput from '@/components/ui/TextInput';
import SubmitButton from '@/components/ui/SubmitButton';

export default function SignupPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

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
    <div className="relative min-h-dvh bg-space">
      <BackButton className="absolute left-4 top-4" />

      <main className="mx-auto flex min-h-dvh max-w-sm flex-col justify-center gap-6 px-6">
        <BrandMark className="justify-center" />

        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            const errors = toFieldErrors(signupSchema, { email, password, nickname });
            setFieldErrors(errors);
            if (Object.keys(errors).length > 0) return;
            signupMutation.mutate({ email, password, nickname });
          }}
        >
          <div className="flex flex-col gap-1">
            <TextInput
              type="email"
              placeholder="이메일"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            {fieldErrors.email && <p className="text-sm text-red-400">{fieldErrors.email}</p>}
          </div>
          <div className="flex flex-col gap-1">
            <TextInput
              type="password"
              placeholder="비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            {fieldErrors.password && <p className="text-sm text-red-400">{fieldErrors.password}</p>}
          </div>
          <div className="flex flex-col gap-1">
            <TextInput
              type="text"
              placeholder="닉네임"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              required
            />
            {fieldErrors.nickname && <p className="text-sm text-red-400">{fieldErrors.nickname}</p>}
          </div>
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
      </main>
    </div>
  );
}
