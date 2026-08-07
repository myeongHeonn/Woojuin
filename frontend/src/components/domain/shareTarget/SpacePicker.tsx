import Dropdown from '@/components/ui/Dropdown';
import Dot from '@/components/ui/Dot';
import { ChevronDownIcon, PlanetIcon, WorkspacesIcon } from '@/assets/icons';
import { classNames } from '@/utils/classNames';
import type { Workspace } from '@/services/workspaces';

interface SpacePickerProps {
  workspaces: Workspace[];
  selectedId: number | null;
  disabled?: boolean;
  onSelect: (id: number) => void;
}

/**
 * 저장 위치 선택기 — 개인 스페이스와 팀 워크스페이스를 구분해 고른다.
 *
 * 구조는 사이드바·크롬 익스텐션(extension/src/popup/SpacePicker.tsx)과 같게 뒀다: 개인이 먼저
 * 오고, 팀은 'Workspaces' 그룹 헤더 아래로 묶이고, 선택 항목에 점이 붙는다. 같은 판단을
 * 세 입구에서 다르게 그리면 사용자가 매번 다시 읽어야 한다.
 *
 * 개인 스페이스는 하나뿐이라 서버 이름('My Space' 등) 대신 'Personal Space' 로 고정한다 —
 * 항목 자체가 곧 그 스페이스다.
 */
const SpacePicker = ({ workspaces, selectedId, disabled = false, onSelect }: SpacePickerProps) => {
  // 서버가 주는 type 하나로 가른다 (useSpaces 와 같은 기준)
  const personal = workspaces.find((workspace) => workspace.type === 'PERSONAL');
  const teams = workspaces.filter((workspace) => workspace.type === 'TEAM');
  const selected = workspaces.find((workspace) => workspace.id === selectedId);

  const labelOf = (workspace: Workspace) =>
    workspace.type === 'PERSONAL' ? 'Personal Space' : workspace.name;

  const renderItem = (workspace: Workspace, close: () => void) => {
    const active = workspace.id === selectedId;
    return (
      <button
        key={workspace.id}
        type="button"
        role="menuitem"
        aria-current={active ? 'true' : undefined}
        onClick={() => {
          onSelect(workspace.id);
          close();
        }}
        className={classNames(
          'flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px]',
          'transition-colors hover:bg-surface-3 [&>svg]:h-4 [&>svg]:w-4',
          active ? 'text-text-1' : 'text-text-2',
        )}
      >
        <PlanetIcon className="shrink-0" />
        <span className="min-w-0 flex-1 truncate">{labelOf(workspace)}</span>
        {active && <Dot size="sm" className="ml-auto" />}
      </button>
    );
  };

  return (
    <Dropdown
      align="left"
      // w-full min-w-0 — 긴 워크스페이스 이름이 들어와도 부풀지 않고 … 로 잘리게
      className="w-full min-w-0"
      renderTrigger={(toggle, open) => (
        <button
          type="button"
          disabled={disabled || workspaces.length === 0}
          aria-label="저장할 곳 선택"
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={toggle}
          className={classNames(
            'flex h-11 w-full items-center gap-2.5 rounded-lg border border-border bg-surface-2 px-3',
            'text-left text-sm text-text-1 transition-colors [&>svg]:h-4 [&>svg]:w-4',
            'disabled:opacity-60',
          )}
        >
          <PlanetIcon className="shrink-0 text-text-3" />
          <span className="min-w-0 flex-1 truncate">
            {selected ? labelOf(selected) : '저장할 곳을 고르세요'}
          </span>
          <ChevronDownIcon
            className={classNames(
              'shrink-0 text-text-3 transition-transform',
              open && 'rotate-180',
            )}
          />
        </button>
      )}
    >
      {(close) => (
        <div className="flex w-full min-w-[220px] flex-col">
          {personal && renderItem(personal, close)}
          {teams.length > 0 && (
            <>
              <div className="flex items-center gap-2 px-2.5 pb-1.5 pt-2.5 text-label font-semibold tracking-[0.16em] text-text-3 [&>svg]:h-4 [&>svg]:w-4">
                <WorkspacesIcon />
                Workspaces
              </div>
              {teams.map((workspace) => renderItem(workspace, close))}
            </>
          )}
        </div>
      )}
    </Dropdown>
  );
};

export default SpacePicker;
