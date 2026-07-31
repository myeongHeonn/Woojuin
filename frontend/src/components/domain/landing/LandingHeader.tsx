import { Link } from 'react-router-dom';
import BrandMark from '@/components/ui/BrandMark';

/**
 * 랜딩 상단 헤더 — 목업 v3.5/landing.html 의 `.nav`.
 *
 * 목업에는 기능·둘러보기 링크가 더 있지만, 지금은 오른쪽 끝 로그인 버튼 하나만 둔다.
 * 모바일과 데스크톱 랜딩에서 함께 사용한다.
 */
const LandingHeader = () => (
  <nav className="fixed inset-x-0 top-0 z-20 flex items-center gap-3 border-b border-border-soft bg-space/80 px-4 py-3.5 backdrop-blur-lg desktop:px-7">
    <BrandMark />

    <Link
      to="/login"
      className="ml-auto rounded-[10px] bg-accent px-3.5 py-2 text-[13.5px] font-semibold text-white transition-colors hover:bg-accent-hover"
    >
      로그인
    </Link>
  </nav>
);

export default LandingHeader;
