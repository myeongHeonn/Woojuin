import { Outlet, useNavigate } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { accessTokenAtom } from '@/stores/authAtoms';
import { fcmTokenAtom } from '@/stores/pushAtoms';
import { requestPushToken } from '@/services/pushNotifications';
import { registerNotificationToken } from '@/services/notifications';

const AuthLayout = () => {
  const accessToken = useAtomValue(accessTokenAtom);
  const setFcmToken = useSetAtom(fcmTokenAtom);
  const navigate = useNavigate();
  // StrictMode(dev)가 effect를 두 번 실행하면 getToken()이 거의 동시에 두 번 불려
  // 서로 다른 FCM 토큰이 발급되고, 그중 하나는 곧바로 무효화(NotRegistered)된다.
  const pushTokenRequested = useRef(false);

  useEffect(() => {
    if (!accessToken) {
      navigate('/', { replace: true });
    }
  }, [accessToken, navigate]);

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
