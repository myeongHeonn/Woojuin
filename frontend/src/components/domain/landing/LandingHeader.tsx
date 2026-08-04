import { Link } from 'react-router-dom';
import BrandMark from '@/components/ui/BrandMark';

/**
 * 랜딩 상단 헤더 — 목업 v3.5/landing.html 의 `.nav`.
 *
 * 목업에는 기능·둘러보기 링크가 더 있지만, 지금은 오른쪽 끝 로그인 버튼 하나만 둔다.
 * 모바일과 데스크톱 랜딩에서 함께 사용한다.
 *
 * 상단 여백에 안전영역을 더한다 — PWA 는 cold start 마다 start_url(`/`)로 들어오므로
 * 로그인 전 사용자는 홈 화면 앱에서 이 헤더를 먼저 만난다. 14px 만 두면 iOS 상태바에
 * 로그인 버튼이 가린다 (theme.css --safe-top).
 */
const LandingHeader = () => (
  <nav className="fixed inset-x-0 top-0 z-20 flex items-center gap-3 border-b border-border-soft bg-space/80 px-4 pb-3.5 pt-[calc(14px+var(--safe-top))] backdrop-blur-lg desktop:px-7">
    <BrandMark />

    <Link
      to="/login"
      // replace 인 이유: 로그인 뒤에도 히스토리에 랜딩이 남으면, 뒤로가기가 `/` 로 갔다가
      // GuestOnly 가 앱으로 되돌려서 "눌러도 아무 일이 없는" 상태가 된다. 로그인은 되돌아올
      // 화면이 아니라 지나가는 관문이므로 항목을 남기지 않는다.
      replace
      className="ml-auto rounded-[10px] bg-accent px-3.5 py-2 text-[13.5px] font-semibold text-white transition-colors hover:bg-accent-hover"
    >
      로그인
    </Link>
  </nav>
);

export default LandingHeader;
