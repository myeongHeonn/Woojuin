import type { SVGProps } from 'react';

const base: SVGProps<SVGSVGElement> = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
};

/** 링크(사선 화살표) — 카드 우상단 링크 배지 */
export function LinkArrowIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} strokeWidth={2.4} {...props}>
      <path d="M7 17 17 7M9 7h8v8" />
    </svg>
  );
}

/** 별 — 즐겨찾기 */
export function StarIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z" />
    </svg>
  );
}

/** 톱니 — 워크스페이스 설정 */
export function SettingsIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="3.2" />
      <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
    </svg>
  );
}

/** 슬라이더 — 관리 */
export function ManageIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <line x1="4" y1="8" x2="20" y2="8" />
      <line x1="4" y1="16" x2="20" y2="16" />
      <circle cx="9" cy="8" r="2.2" fill="currentColor" stroke="none" />
      <circle cx="15" cy="16" r="2.2" fill="currentColor" stroke="none" />
    </svg>
  );
}

/** 홑화살표 왼쪽 — 가로 스크롤 이전 */
export function ChevronLeftIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="m15 6-6 6 6 6" />
    </svg>
  );
}

/** 홑화살표 오른쪽 — 가로 스크롤 다음 */
export function ChevronRightIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="m9 6 6 6-6 6" />
    </svg>
  );
}

/** 행성(토성) — Personal Space·워크스페이스 항목 */
export function PlanetIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="3.4" fill="currentColor" stroke="none" />
      <ellipse cx="12" cy="12" rx="9" ry="3.3" transform="rotate(-18 12 12)" />
    </svg>
  );
}

/** 휴지통 */
export function TrashIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M6 7l1 13h10l1-13" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  );
}

/** 되돌리기(반시계 화살표) — 휴지통 복구 */
export function RestoreIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M4 5v5h5" />
      <path d="M4.5 10a8 8 0 1 1-1.3 5" />
    </svg>
  );
}

/** 홑화살표 아래 — 드롭다운/전환 표시 */
export function ChevronDownIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

/** 돋보기 — 검색 */
export function SearchIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.35-4.35" />
    </svg>
  );
}

/** 반짝(스파크) — AI 모드 */
export function SparkIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z" />
    </svg>
  );
}

/** 사람 + 플러스 — 공유(멤버 초대) */
export function UserPlusIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19c0-3 2.5-5.2 5.5-5.2s5.5 2.2 5.5 5.2" />
      <path d="M18.5 8.5v5M16 11h5" />
    </svg>
  );
}

/** 가로 점 3개 — 더보기 메뉴 */
export function DotsHorizontalIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="5" cy="12" r="1.4" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none" />
      <circle cx="19" cy="12" r="1.4" fill="currentColor" stroke="none" />
    </svg>
  );
}

/** 다운로드(아래 화살표 + 받침) — 원본 이미지 저장 */
export function DownloadIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3v11m0 0 4-4m-4 4-4-4" />
      <path d="M4 17v1.5A2.5 2.5 0 0 0 6.5 21h11a2.5 2.5 0 0 0 2.5-2.5V17" />
    </svg>
  );
}

/** 사람 — 마이페이지 */
export function UserIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="8" r="3.6" />
      <path d="M4.5 20c0-3.6 3.4-6 7.5-6s7.5 2.4 7.5 6" />
    </svg>
  );
}

/** 왼쪽 화살표 — 뒤로 가기 */
export function ArrowLeftIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M19 12H5" />
      <path d="m12 19-7-7 7-7" />
    </svg>
  );
}

/** 더하기 — 추가·새로 만들기 */
export function PlusIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

/** 연필 — 이름 수정 */
export function PencilIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      {/*
        몸통 — 심 끝(3,20)으로 모이는 삼각형 + 축을 따라 올라가는 사각 몸통 + 꼭지 라운드.
        이전 경로는 두 곳이 어긋나 있었다: 밑변이 (4,20)·(5,20)·(8,20) 세 점을 지나 심 끝이
        뾰족하지 않고 뭉툭한 블록이 됐고, 꼭지 쪽에서는 선이 몸통 밖으로 가시처럼 튀어나왔다.
      */}
      <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7.5 18.5 3 20l1.5-4.5 12-12z" />
      {/* 나무와 지우개를 가르는 띠 — 축에 수직이고 몸통 폭을 정확히 가로지른다(3,3).
          이전 값(4,4)은 한 유닛 길어서 오른쪽 변을 뚫고 나왔다 */}
      <path d="m13.5 6.5 3 3" />
    </svg>
  );
}

/** 체크 — 확정·저장 */
export function CheckIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="m5 12 5 5L20 7" />
    </svg>
  );
}

/** 별자리 — 뷰바 "성좌" */
export function ConstellationIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="6" cy="7" r="1.4" />
      <circle cx="18" cy="6" r="1.4" />
      <circle cx="13" cy="13" r="1.4" />
      <circle cx="7" cy="17" r="1.4" />
      <path d="M6 7 13 13M18 6 13 13M13 13 7 17" />
    </svg>
  );
}

/** 2×2 격자 — 뷰바 "대시보드" */
export function DashboardIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  );
}

/** 접힌 지도 — 뷰바 "지도" */
export function MapIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3z" />
      <path d="M9 3v15M15 6v15" />
    </svg>
  );
}

/** 패널 분할 — 뷰바 "한눈에 보기" */
export function OverviewIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M3 10h18M9 10v10" />
    </svg>
  );
}

/** 종 — 알림 버튼 */
export function BellIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M18 9a6 6 0 1 0-12 0c0 5-2 6-2 6h16s-2-1-2-6" />
      <path d="M10.3 19a2 2 0 0 0 3.4 0" />
    </svg>
  );
}

/** 큐브 — Workspaces 그룹 헤더 */
export function WorkspacesIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="m3 8 9-5 9 5-9 5z" />
      <path d="m3 8 9 5 9-5M3 8v8l9 5 9-5V8" />
    </svg>
  );
}
