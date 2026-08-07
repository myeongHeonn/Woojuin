import type { ComponentType, SVGProps } from 'react';
import { Link, useLocation, useMatch } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import { IS_INSTALLED_APP } from '@/utils/installedApp';
import { useSpaces } from '@/hooks/useSpaces';
import { ConstellationIcon, DashboardIcon, MapIcon, UserIcon } from '@/assets/icons';

interface Tab {
  label: string;
  Icon: ComponentType<SVGProps<SVGSVGElement>>;
  /** 워크스페이스 하위 경로 조각 — 지금 보고 있는 워크스페이스에 붙는다 */
  segment?: string;
  /** 워크스페이스와 무관한 절대 경로 */
  path?: string;
}

/**
 * 탭 네 개. 앞 셋은 같은 워크스페이스를 다르게 보는 뷰라 segment 를 쓰고,
 * 마이는 워크스페이스에 속하지 않아 path 를 쓴다.
 * 휴지통은 워크스페이스별이라 탭이 아니라 스테이지 헤더의 휴지통 아이콘으로 들어간다.
 * 화면에 구분선은 두지 않는다 — 아이콘과 순서로 충분히 읽힌다.
 */
const TABS: Tab[] = [
  { label: '우주', Icon: ConstellationIcon, segment: 'universe' },
  { label: '보관함', Icon: DashboardIcon, segment: 'library' },
  { label: '지도', Icon: MapIcon, segment: 'map' },
  { label: '마이', Icon: UserIcon, path: '/my' },
];

/**
 * 모바일 하단 탭바 — 데스크톱 ViewBar 를 키운 형태다.
 * 테두리 있는 둥근 컨테이너에 활성 탭만 surface-3 로 채운다.
 *
 * 성좌 캔버스 위에 떠 있으므로(absolute) 스크롤이 있는 화면은
 * 아래쪽에 탭바 높이만큼 여백을 둬야 내용이 가리지 않는다.
 */
const TabBar = ({ className }: { className?: string }) => {
  const { pathname } = useLocation();
  const { personalSpaceId } = useSpaces();

  /**
   * TabBar 는 /workspace/:workspaceId 라우트보다 위에 있어 useParams 로는 id 를 못 읽는다.
   * 마이·휴지통에 있는 동안에는 URL 에 워크스페이스가 없으므로 개인 스페이스로 되돌린다.
   */
  const match = useMatch('/workspace/:workspaceId/*');
  // 목록 로드 전(personalSpaceId undefined)엔 워크스페이스 탭을 /home 으로 보낸다
  const workspaceId = match?.params.workspaceId ?? personalSpaceId;

  return (
    <nav
      aria-label="주요 화면"
      className={classNames(
        // 데스크톱(>= breakpoint-desktop)에서는 사이드바가 대신하므로 감춘다.
        // SideBar 가 hidden desktop:flex 를 자기 안에 두는 것과 짝을 이룬다
        'desktop:hidden px-3.5 pb-[var(--tabbar-bottom-pad)]',
        className,
      )}
    >
      <div className="flex gap-1 rounded-[20px] border border-border bg-sidebar/70 p-1.5 backdrop-blur-lg">
        {TABS.map(({ label, Icon, segment, path }) => {
          const to = path ?? (workspaceId ? `/workspace/${workspaceId}/${segment}` : '/home');
          // 하위 경로(library/:catId)에서도 켜져 보이게 prefix 로 판단
          const active = pathname === to || pathname.startsWith(`${to}/`);

          return (
            <Link
              key={label}
              data-tutorial-view={segment}
              to={to}
              // 설치된 앱에서는 탭 전환이 히스토리를 쌓지 않는다 — 네이티브 앱의 탭과 같다.
              // 그래야 뷰에서 뒤로가기가 앱을 닫고(안드로이드), 엣지 스와이프가 무반응이다(iOS).
              // 브라우저 탭에서는 뒤로가기가 정상 동작해야 하므로 그대로 쌓는다.
              replace={IS_INSTALLED_APP}
              title={label}
              aria-label={label}
              aria-current={active ? 'page' : undefined}
              className={classNames(
                'grid h-[46px] flex-1 place-items-center rounded-[15px] transition-colors',
                '[&>svg]:h-[23px] [&>svg]:w-[23px]',
                active ? 'bg-surface-3 text-text-1' : 'text-text-3',
              )}
            >
              <Icon />
            </Link>
          );
        })}
      </div>
    </nav>
  );
};

export default TabBar;
