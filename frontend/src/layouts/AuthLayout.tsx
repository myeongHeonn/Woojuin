import { Outlet, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { accessTokenAtom } from '@/stores/authAtoms';
import { fcmTokenAtom } from '@/stores/pushAtoms';
import { requestPushToken } from '@/services/pushNotifications';
import { registerNotificationToken } from '@/services/notifications';

const AuthLayout = () => {
  const accessToken = useAtomValue(accessTokenAtom);
  const setFcmToken = useSetAtom(fcmTokenAtom);
  const navigate = useNavigate();

  useEffect(() => {
    if (!accessToken) {
      navigate('/', { replace: true });
    }
  }, [accessToken, navigate]);

  useEffect(() => {
    if (!accessToken) return;

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
