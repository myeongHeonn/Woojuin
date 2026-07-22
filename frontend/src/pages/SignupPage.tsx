import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { signup } from '@/services/auth';

export default function SignupPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');

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
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-4 px-6">
      <h1 className="text-xl font-bold">회원가입</h1>

      <form
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          signupMutation.mutate({ email, password, nickname });
        }}
      >
        <input
          type="email"
          placeholder="이메일"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="rounded border px-3 py-2"
          required
        />
        <input
          type="password"
          placeholder="비밀번호"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="rounded border px-3 py-2"
          required
        />
        <input
          type="text"
          placeholder="닉네임"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          className="rounded border px-3 py-2"
          required
        />
        {errorMessage && <p className="text-sm text-red-600">{errorMessage}</p>}
        <button
          type="submit"
          disabled={signupMutation.isPending}
          className="rounded bg-slate-900 px-3 py-2 text-white disabled:opacity-50"
        >
          {signupMutation.isPending ? '가입 중...' : '회원가입'}
        </button>
      </form>

      <p className="text-sm">
        이미 계정이 있으신가요?{' '}
        <Link to="/login" className="underline">
          로그인
        </Link>
      </p>
    </main>
  );
}
