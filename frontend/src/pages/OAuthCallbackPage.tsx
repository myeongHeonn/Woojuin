import { useEffect } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { accessTokenAtom, postLoginRedirectAtom, refreshTokenAtom } from '@/stores/authAtoms';

/**
 * 구글 로그인 성공 시 백엔드(OAuth2LoginSuccessHandler)가
 * accessToken/refreshToken을 쿼리스트링에 담아 이 경로로 리다이렉트한다.
 */
export default function OAuthCallbackPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setAccessToken = useSetAtom(accessTokenAtom);
  const setRefreshToken = useSetAtom(refreshTokenAtom);
  const [postLoginRedirect, setPostLoginRedirect] = useAtom(postLoginRedirectAtom);

  useEffect(() => {
    const accessToken = searchParams.get('accessToken');
    const refreshToken = searchParams.get('refreshToken');

    if (accessToken && refreshToken) {
      setAccessToken(accessToken);
      setRefreshToken(refreshToken);
      // 로그인 전 가려던 경로가 있으면 그리로, 없으면 기본 도착지(/home)로.
      setPostLoginRedirect(null);
      navigate(postLoginRedirect ?? '/home', { replace: true });
    } else {
      navigate('/login', { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p>로그인 처리 중...</p>
    </main>
  );
}
