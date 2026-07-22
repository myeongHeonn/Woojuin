import { createBrowserRouter } from 'react-router-dom';
import AuthLayout from '@/layouts/AuthLayout';
import Layout from '@/layouts/Layout';
import HomePage from '@/pages/HomePage';
import WorkspacePage from '@/pages/WorkspacePage';
import ShareTargetPage from '@/pages/ShareTargetPage';
import LandingPage from '@/pages/LandingPage';
import LoginPage from '@/pages/LoginPage';
import SignupPage from '@/pages/SignupPage';
import OAuthCallbackPage from '@/pages/OAuthCallbackPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <LandingPage />,
  },
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  // 구글 OAuth 콜백 (백엔드 woojuin.oauth.redirect-base-url과 경로 일치 필요)
  { path: '/oauth/callback', element: <OAuthCallbackPage /> },
  // 로그인해야 들어갈 수 있는 앱 화면들 (사이드바 포함)
  {
    element: <AuthLayout />,
    children: [
      {
        element: <Layout />,
        children: [
          { path: '/home', element: <HomePage /> },
          { path: '/workspace/:workspaceId', element: <WorkspacePage /> },
        ],
      },
    ],
  },
  // Web Share Target 진입점 (manifest.share_target.action)
  // OS 공유 시트에서 바로 진입하는 화면이라 사이드바 없이 단독 렌더
  { path: '/share-target', element: <ShareTargetPage /> },
]);
