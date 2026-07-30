import { createBrowserRouter, Navigate } from 'react-router-dom';
import { lazy, Suspense, type ReactNode } from 'react';
import Spinner from '@/components/ui/Spinner';
// 레이아웃은 구조용이라 가볍다 — eager 로 둬 셸이 즉시 뜨게 한다.
import AuthLayout from '@/layouts/AuthLayout';
import Layout from '@/layouts/Layout';
import StageLayout from '@/layouts/StageLayout';
import WorkspaceLayout from '@/layouts/WorkspaceLayout';
import DesktopOnly from '@/layouts/DesktopOnly';

// 페이지는 lazy 로 분할한다 — 특히 성좌(three)·지도(maplibre)가 무거워서,
// 이걸 나눠야 랜딩·로그인 첫 로딩에 그 라이브러리가 안 딸려온다.
const PersonalSpacePage = lazy(() => import('@/pages/PersonalSpacePage'));
const ShareTargetPage = lazy(() => import('@/pages/ShareTargetPage'));
const LandingPage = lazy(() => import('@/pages/LandingPage'));
const UniversePage = lazy(() => import('@/pages/UniversePage'));
const LibraryPage = lazy(() => import('@/pages/LibraryPage'));
const MyPage = lazy(() => import('@/pages/MyPage'));
const TrashPage = lazy(() => import('@/pages/TrashPage'));
const MapPage = lazy(() => import('@/pages/MapPage'));
const CanvasPage = lazy(() => import('@/pages/CanvasPage'));
const LoginPage = lazy(() => import('@/pages/LoginPage'));
const SignupPage = lazy(() => import('@/pages/SignupPage'));
const OAuthCallbackPage = lazy(() => import('@/pages/OAuthCallbackPage'));
const InvitePage = lazy(() => import('@/pages/InvitePage'));
const PrivacyPolicyPage = lazy(() => import('@/pages/PrivacyPolicyPage'));

/** lazy 페이지가 청크를 받아오는 동안 보여줄 로딩 자리 */
const page = (node: ReactNode) => (
  <Suspense
    fallback={
      <div className="grid min-h-[50vh] w-full place-items-center bg-space">
        <Spinner className="h-6 w-6" />
      </div>
    }
  >
    {node}
  </Suspense>
);

export const router = createBrowserRouter([
  {
    path: '/',
    element: page(<LandingPage />),
  },
  { path: '/login', element: page(<LoginPage />) },
  { path: '/signup', element: page(<SignupPage />) },
  // 공유 링크 진입점 — 로그인 전에도 미리보기, 참여는 로그인 후(사이드바 없는 단독 화면)
  { path: '/invite/:code', element: page(<InvitePage />) },
  // 개인정보처리방침 — 가입 전에도 읽을 수 있어야 하므로 로그인 없이 접근 가능
  { path: '/privacy', element: page(<PrivacyPolicyPage />) },
  // 구글 OAuth 콜백 (백엔드 woojuin.oauth.redirect-base-url과 경로 일치 필요)
  { path: '/oauth/callback', element: page(<OAuthCallbackPage />) },
  // 로그인해야 들어갈 수 있는 앱 화면들 (사이드바 포함)
  {
    element: <AuthLayout />,
    children: [
      {
        element: <Layout />,
        children: [
          // 로그인 직후 도착지 — 개인 워크스페이스 성좌로 넘긴다
          { path: '/home', element: page(<PersonalSpacePage />) },
          // 워크스페이스에 속하지 않는 화면들 (모바일 탭바에서 진입)
          { path: '/my', element: page(<MyPage />) },
          {
            path: '/workspace/:workspaceId',
            // 화면은 안 그리고 변경 신호(SSE) 구독만 한다 — 휴지통까지 한 연결로 덮는다
            element: <WorkspaceLayout />,
            children: [
              // 뷰 없이 들어오면 성좌가 기본
              { index: true, element: <Navigate to="universe" replace /> },
              // 휴지통은 워크스페이스별 — 스테이지 헤더의 휴지통 아이콘으로 진입
              { path: 'trash', element: page(<TrashPage />) },
              // 같은 워크스페이스를 다르게 보는 네 화면 — 상단 헤더(제목·뷰바)를 공유한다
              {
                element: <StageLayout />,
                children: [
                  { path: 'universe', element: page(<UniversePage />) },
                  { path: 'library', element: page(<LibraryPage />) },
                  { path: 'map', element: page(<MapPage />) },
                  // 한눈에 보기는 넓은 화면이 전제라 모바일에서는 성좌로 돌려보낸다
                  {
                    element: <DesktopOnly />,
                    children: [{ path: 'canvas', element: page(<CanvasPage />) }],
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
  // Web Share Target 진입점 (manifest.share_target.action)
  // OS 공유 시트에서 바로 진입하는 화면이라 사이드바 없이 단독 렌더
  { path: '/share-target', element: page(<ShareTargetPage />) },
]);
