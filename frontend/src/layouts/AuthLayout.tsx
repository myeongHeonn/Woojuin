import { Outlet } from 'react-router-dom';
import { useEffect } from 'react';

const AuthLayout = () => {
  // TODO: 인증 가드 — 유저 정보가 없으면 / 로 이동
  //   const navigate = useNavigate();
  //   const location = useLocation();
  useEffect(() => {
    // 유저 정보가 없으면 /로 이동
    // navigate('/');
  }, []);

  return (
    <>
      <Outlet />
    </>
  );
};

export default AuthLayout;
