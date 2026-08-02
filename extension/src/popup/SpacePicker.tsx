import { useEffect, useRef, useState } from 'react';
import { Workspace } from '@/api/workspaces';

/**
 * 저장 위치 선택기 — 웹앱 사이드바의 구조를 그대로 옮긴 드롭다운.
 *
 * 네이티브 select 를 쓰지 않는 이유: 펼침 목록을 OS 가 그려서 아이콘·간격·그룹 헤더 스타일을
 * 맞출 수 없다(다크 배경도 colorScheme 힌트에 의존해야 한다). 팝업에서 유일하게 여러 항목을
 * 고르는 곳이라 여기만큼은 웹앱과 같은 모습이어야 한다.
 *
 * 웹앱과 맞춘 것: 행성 아이콘(PlanetIcon), 큐브 아이콘(WorkspacesIcon), 'Personal Space'·
 * 'Workspaces' 표기, 선택 항목의 accent 점, 대비 규칙(선택=text-1 / 나머지=text-2).
 */

const SURFACE = '#20242f';
const SURFACE_2 = '#2a2e3a';
const SURFACE_3 = '#343947';
const BORDER = '#313543';
const TEXT_1 = '#f0f2f6';
const TEXT_2 = '#b0b6c3';
const TEXT_3 = '#7b8290';
const ACCENT = '#7c6cf0';

const iconBase = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  width: 16,
  height: 16,
  style: { flexShrink: 0 },
} as const;

/** 행성(토성) — 웹앱 PlanetIcon 과 동일 */
const PlanetIcon = () => (
  <svg {...iconBase} aria-hidden="true">
    <circle cx="12" cy="12" r="3.4" fill="currentColor" stroke="none" />
    <ellipse cx="12" cy="12" rx="9" ry="3.3" transform="rotate(-18 12 12)" />
  </svg>
);

/** 큐브 — 웹앱 WorkspacesIcon 과 동일 (Workspaces 그룹 헤더) */
const WorkspacesIcon = () => (
  <svg {...iconBase} aria-hidden="true">
    <path d="m3 8 9-5 9 5-9 5z" />
    <path d="m3 8 9 5 9-5M3 8v8l9 5 9-5V8" />
  </svg>
);

const ChevronDownIcon = ({ open }: { open: boolean }) => (
  <svg
    {...iconBase}
    aria-hidden="true"
    style={{
      ...iconBase.style,
      marginLeft: 'auto',
      transition: 'transform 0.15s ease-out',
      transform: open ? 'rotate(180deg)' : undefined,
    }}
  >
    <path d="m6 9 6 6 6-6" />
  </svg>
);

interface SpacePickerProps {
  workspaces: Workspace[];
  selectedId: number | null;
  disabled?: boolean;
  onSelect: (id: number) => void;
}

const SpacePicker = ({ workspaces, selectedId, disabled = false, onSelect }: SpacePickerProps) => {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  // 서버가 주는 type 하나로 가른다 (웹앱 useSpaces 와 같은 기준)
  const personal = workspaces.find((workspace) => workspace.type === 'PERSONAL') ?? null;
  const shared = workspaces.filter((workspace) => workspace.type !== 'PERSONAL');

  // Personal Space 는 하나뿐이라 서버 이름('My Space' 등)을 따로 보여주지 않는다 —
  // 항목 자체가 곧 그 스페이스다(웹앱 사이드바도 최상위 항목 하나로 둔다).
  const labelOf = (workspace: Workspace) =>
    workspace.type === 'PERSONAL' ? 'Personal Space' : workspace.name;

  const selected = workspaces.find((workspace) => workspace.id === selectedId) ?? null;

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const renderItem = (workspace: Workspace) => {
    const active = workspace.id === selectedId;
    return (
      <button
        key={workspace.id}
        type="button"
        onClick={() => {
          onSelect(workspace.id);
          setOpen(false);
        }}
        style={{ ...styles.item, color: active ? TEXT_1 : TEXT_2 }}
        onPointerEnter={(e) => { e.currentTarget.style.background = SURFACE_3; }}
        onPointerLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
      >
        <PlanetIcon />
        <span style={styles.itemLabel}>{labelOf(workspace)}</span>
        {/* 선택 표시 — 웹앱 사이드바의 accent 점과 같은 방식 */}
        {active && <span style={styles.dot} />}
      </button>
    );
  };

  return (
    <div ref={rootRef} style={styles.root}>
      <button
        type="button"
        disabled={disabled || !workspaces.length}
        aria-label="저장할 곳 선택"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        style={{ ...styles.trigger, opacity: disabled || !workspaces.length ? 0.6 : 1 }}
      >
        <PlanetIcon />
        <span style={styles.itemLabel}>
          {selected ? labelOf(selected) : '저장할 곳을 고르세요'}
        </span>
        <ChevronDownIcon open={open} />
      </button>

      {open && (
        <div role="listbox" style={styles.panel}>
          {personal && renderItem(personal)}
          {shared.length > 0 && (
            <>
              {/* 웹앱 사이드바의 'SPACE' 라벨과 같은 조판(11px·굵게·자간) */}
              <div style={styles.groupHeader}>
                <WorkspacesIcon />
                Workspaces
              </div>
              {shared.map(renderItem)}
            </>
          )}
        </div>
      )}
    </div>
  );
};

const rowBase: React.CSSProperties = {
  boxSizing: 'border-box',
  width: '100%',
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  textAlign: 'left',
  fontSize: 13,
  fontFamily: 'inherit',
  cursor: 'pointer',
};

const styles: Record<string, React.CSSProperties> = {
  // minWidth: 0 — 부모 안에서 내용 폭만큼 부푸는 것을 막는다(Popup 의 label 주석 참고)
  root: { position: 'relative', minWidth: 0 },
  trigger: {
    ...rowBase,
    height: 40,
    padding: '0 10px',
    border: `1px solid ${BORDER}`,
    borderRadius: 12,
    background: SURFACE_2,
    color: TEXT_1,
    fontSize: 14,
  },
  panel: {
    position: 'absolute',
    top: 'calc(100% + 4px)',
    left: 0,
    right: 0,
    zIndex: 10,
    padding: 6,
    border: `1px solid ${BORDER}`,
    borderRadius: 12,
    background: SURFACE,
    boxShadow: '0 12px 28px rgba(0, 0, 0, 0.45)',
    maxHeight: 220,
    overflowY: 'auto',
  },
  item: {
    ...rowBase,
    padding: '8px 10px',
    border: 0,
    borderRadius: 8,
    background: 'transparent',
    transition: 'background-color 0.12s ease-out',
  },
  itemLabel: {
    minWidth: 0,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  groupHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '10px 10px 6px',
    color: TEXT_3,
    fontSize: 11,
    fontWeight: 600,
    letterSpacing: '0.16em',
  },
  dot: {
    marginLeft: 'auto',
    width: 6,
    height: 6,
    borderRadius: 999,
    background: ACCENT,
    flexShrink: 0,
  },
};

export default SpacePicker;
