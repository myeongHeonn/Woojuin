import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import { STAGE_PX } from '@/constants/stage';
import SkyClock from './SkyClock';
import ThemeToggle from './ThemeToggle';
import ViewBar from './ViewBar';
import WorkspaceSwitcher from '@/components/domain/nav/WorkspaceSwitcher';

interface StageHeaderProps {
  /** 워크스페이스 이름 */
  title: string;
  /** 제목 아래 한 줄 요약 (예: "128 memories · 12 constellations") */
  meta?: string;
  /**
   * 뷰바 왼쪽에 붙는 자리 — 멤버 아바타·알림·공유가 들어간다.
   * 셋 다 API 가 아직 없어 비워둔 상태다.
   */
  actions?: ReactNode;
}

/**
 * 스테이지(성좌·대시보드·지도) 위에 떠 있는 상단 헤더.
 *
 * 캔버스를 덮고 있으므로 기본은 pointer-events-none 이고,
 * 실제로 눌러야 하는 오른쪽 묶음만 다시 켠다. 그래야 제목 옆 빈 공간에서도
 * 별을 드래그해 우주를 돌릴 수 있다.
 */
const StageHeader = ({ title, meta, actions }: StageHeaderProps) => {
  const { pathname } = useLocation();
  /** 성좌뷰에서만 헤더가 하늘 위에 얹힌다 — 대시보드·지도는 배경이 표면이다. */
  const onSky = pathname.endsWith('/universe');

  return (
    <header
      className={classNames(
        // 상단 여백에 안전영역을 더한다 — iOS 홈 화면 앱에서 pt-5(20px)만 두면 상태바
        // (47~59px) 밑에 헤더가 들어가 오른쪽 버튼들이 눌리지 않았다 (theme.css --safe-top).
        'pointer-events-none absolute inset-x-0 top-0 z-40 flex items-start justify-between gap-4',
        'pt-[calc(20px+var(--safe-top))] desktop:pt-[calc(26px+var(--safe-top))]',
        STAGE_PX,
      )}
    >
      {/*
      성좌뷰에서는 이 글자들이 **하늘 위**에 얹힌다. 테마 글자색은 표면 위에서 읽히도록 정해진
      값이라 낮에는 어두워지는데, 그 아래는 표면이 아니라 짙은 파란 하늘이다 — 제목 1.2:1,
      요약 1.3:1 까지 떨어졌다. 하늘 꼭대기는 한낮에도 밝아지지 않으므로 밝은 글자로 고정한다
      (index.css 의 .woojuin-on-sky).

      대시보드·지도는 배경이 표면이라 기존 색이 맞다 — 거기서 밝은 글자로 고정하면 반대로
      라이트에서 사라진다.
    */}
      <div className={classNames('min-w-0', onSky && 'woojuin-on-sky')}>
        {/* 모바일: 이름 탭 → 워크스페이스 전환(사이드바 없음) / 데스크톱: 그냥 제목 */}
        <div className="pointer-events-auto min-w-0 desktop:hidden" data-tutorial="workspace-title">
          <WorkspaceSwitcher />
        </div>
        <h1
          className={classNames(
            'hidden truncate text-2xl font-extrabold tracking-[-0.01em] desktop:block desktop:text-[30px]',
            !onSky && 'text-text-1',
          )}
        >
          {title}
        </h1>
        {/* 하늘 위에서는 색을 낮추지 않는다 — 요약이 12~14px 이라 흐리면 4.5:1 을 못 넘긴다.
          위계는 크기와 굵기가 이미 충분히 만든다. */}
        {meta && (
          <p
            className={classNames(
              'mt-[3px] truncate text-xs desktop:text-sm',
              !onSky && 'text-text-2',
            )}
          >
            {meta}
          </p>
        )}
      </div>

      {/* 세로로 쌓는다 — 버튼 줄 아래에 남는 자리를 시계가 쓴다(현재시간 테마에서만 나온다).
        items-end 로 오른쪽 끝을 맞춰야 시계와 뷰바의 오른쪽 선이 이어진다. */}
      <div className="pointer-events-auto flex shrink-0 flex-col items-end gap-2">
        <div className="flex items-center gap-2">
          {actions}
          <ThemeToggle />
          <ViewBar />
        </div>
        <SkyClock />
      </div>
    </header>
  );
};

export default StageHeader;
