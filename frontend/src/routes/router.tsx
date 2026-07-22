import { createBrowserRouter } from 'react-router-dom';
import Layout from '@/layouts/Layout';
import HomePage from '@/pages/HomePage';
import ShareTargetPage from '@/pages/ShareTargetPage';
import LandingPage from '@/pages/LandingPage';
import UniversePage from '@/pages/UniversePage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <LandingPage />,
  },
  // 사이드바가 있는 앱 화면들
  {
    element: <Layout />,
    children: [
      { path: '/home', element: <HomePage /> },
      { path: '/universe', element: <UniversePage /> },
    ],
  },
  // Web Share Target 진입점 (manifest.share_target.action)
  // OS 공유 시트에서 바로 진입하는 화면이라 사이드바 없이 단독 렌더
  { path: '/share-target', element: <ShareTargetPage /> },
]);
