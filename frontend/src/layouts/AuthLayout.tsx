import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { accessTokenAtom, postLoginRedirectAtom } from '@/stores/authAtoms';
import { fcmTokenAtom } from '@/stores/pushAtoms';
import { requestPushToken } from '@/services/pushNotifications';
import { registerNotificationToken } from '@/services/notifications';

const AuthLayout = () => {
  const accessToken = useAtomValue(accessTokenAtom);
  const setFcmToken = useSetAtom(fcmTokenAtom);
  const setPostLoginRedirect = useSetAtom(postLoginRedirectAtom);
  const navigate = useNavigate();
  const location = useLocation();
  // StrictMode(dev)가 effect를 두 번 실행하면 getToken()이 거의 동시에 두 번 불려
  // 서로 다른 FCM 토큰이 발급되고, 그중 하나는 곧바로 무효화(NotRegistered)된다.
  const pushTokenRequested = useRef(false);

  useEffect(() => {
    if (!accessToken) {
      // 로그인 후 GoogleAuthButton이 백엔드로 전체 페이지 이동하는데,
      // 이 화면은 그 전에 이미 사라지므로 목적지를 여기서 미리 남겨둔다.
      setPostLoginRedirect(location.pathname + location.search);
      navigate('/', { replace: true });
    }
  }, [accessToken, navigate, location, setPostLoginRedirect]);

  useEffect(() => {
    if (!accessToken || pushTokenRequested.current) return;
    pushTokenRequested.current = true;

    requestPushToken().then((token) => {
      if (!token) return;
      setFcmToken(token);
      registerNotificationToken(token, navigator.userAgent).catch(() => {
        // 등록 실패해도 로그인 흐름 자체는 막지 않는다
      });
    });
  }, [accessToken, setFcmToken]);

  if (!accessToken) return null;

  return (
    <>
      <Outlet />
    </>
  );
};

export default AuthLayout;
