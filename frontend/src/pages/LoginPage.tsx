import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useSetAtom } from 'jotai';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { login } from '@/services/auth';
import { backendOrigin } from '@/services/client';
import { accessTokenAtom, refreshTokenAtom } from '@/stores/authAtoms';

export default function LoginPage() {
  const navigate = useNavigate();
  const setAccessToken = useSetAtom(accessTokenAtom);
  const setRefreshToken = useSetAtom(refreshTokenAtom);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (tokens) => {
      setAccessToken(tokens.accessToken);
      setRefreshToken(tokens.refreshToken);
      navigate('/home');
    },
  });

  const errorMessage = axios.isAxiosError(loginMutation.error)
    ? (loginMutation.error.response?.data?.message ?? '로그인에 실패했습니다')
    : null;

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-4 px-6">
      <h1 className="text-xl font-bold">로그인</h1>

      <form
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          loginMutation.mutate({ email, password });
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
        {errorMessage && <p className="text-sm text-red-600">{errorMessage}</p>}
        <button
          type="submit"
          disabled={loginMutation.isPending}
          className="rounded bg-slate-900 px-3 py-2 text-white disabled:opacity-50"
        >
          {loginMutation.isPending ? '로그인 중...' : '로그인'}
        </button>
      </form>

      <a
        href={`${backendOrigin}/oauth2/authorization/google`}
        className="rounded border px-3 py-2 text-center"
      >
        구글로 로그인
      </a>

      <p className="text-sm">
        계정이 없으신가요?{' '}
        <Link to="/signup" className="underline">
          회원가입
        </Link>
      </p>
    </main>
  );
}
