import { createBrowserRouter, Navigate } from 'react-router-dom';
import { lazy, Suspense, type ReactNode } from 'react';
import Spinner from '@/components/ui/Spinner';
// 레이아웃은 구조용이라 가볍다 — eager 로 둬 셸이 즉시 뜨게 한다.
import AuthLayout from '@/layouts/AuthLayout';
import Layout from '@/layouts/Layout';
import StageLayout from '@/layouts/StageLayout';
import WorkspaceLayout from '@/layouts/WorkspaceLayout';
import DesktopOnly from '@/layouts/DesktopOnly';
// 에러 화면은 lazy 로 두지 않는다 — 청크를 못 받아서 생긴 에러를 보여주려고
// 또 청크를 받아야 하면 그 화면도 같이 실패한다.
import ErrorPage from '@/pages/ErrorPage';
import NotFoundPage from '@/pages/NotFoundPage';

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
    /*
     * 경로 없는 최상단 라우트 — 화면은 그리지 않고(children 만 있으면 라우터가 Outlet 을
     * 자동으로 끼운다) **에러 경계 자리**로만 쓴다.
     *
     * 여기 한 곳에 두면 하위 어느 화면에서 터져도 전부 ErrorPage 로 모인다. 라우트마다
     * errorElement 를 달면 새 화면을 추가할 때마다 잊기 쉽고, 잊은 화면은 조용히
     * 빈 화면으로 돌아간다.
     */
    errorElement: <ErrorPage />,
    children: [
      {
        path: '/',
        element: page(<LandingPage />),
      },
      { path: '/login', element: page(<LoginPage />) },
      // 구글 로그인이 막혀 있는 동안(LoginForm.GOOGLE_LOGIN_ENABLED 주석 참고) 이메일 가입이
      // 유일한 가입 경로다. 구글은 첫 로그인이 곧 가입이라 그동안 이 라우트를 닫아뒀었다.
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
      /*
       * 위 어디에도 안 걸리는 주소 — 반드시 마지막에 둔다.
       *
       * 이걸 두지 않으면 라우터가 404 를 위 errorElement 로 올려보내는데, 그러면 주소가
       * 없다는 흔한 상황이 "에러"로 취급돼 콘솔에 오류가 찍힌다. 진짜 고장과 구분되게
       * 정상 라우트로 받는다.
       */
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
