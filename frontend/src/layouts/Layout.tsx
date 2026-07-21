import { Outlet } from 'react-router-dom';
import SideBar from '@/components/domain/nav/SideBar';

/**
 * 앱 공통 셸 — 목업의 `.app { display:grid; grid-template-columns:300px 1fr }`.
 * 사이드바가 자기 폭(300px ↔ 72px)을 직접 관리하므로 grid 고정폭 대신 flex 로 두어
 * 접힘 애니메이션이 그대로 따라오게 한다.
 */
const Layout = () => {
  return (
    <div className="flex h-screen overflow-hidden">
      <SideBar />
      <main className="relative flex-1 overflow-hidden bg-space">
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;
