import { useState } from 'react';
import Dropdown from '@/components/ui/Dropdown';
import MenuItem from '@/components/ui/MenuItem';
import RenameWorkspaceModal from '@/components/domain/nav/RenameWorkspaceModal';
import DeleteWorkspaceModal from '@/components/domain/share/DeleteWorkspaceModal';
import { DotsHorizontalIcon, PencilIcon, TrashIcon } from '@/assets/icons';
import { classNames } from '@/utils/classNames';
import type { Workspace } from '@/services/workspaces';

interface WorkspaceActionsMenuProps {
  workspace: Workspace;
}

/**
 * 사이드바 워크스페이스 행의 ⋮ 메뉴 — 이름 변경 · 삭제.
 *
 * 두 동작 모두 원래 스테이지 헤더의 공유 설정 안에만 있었다. 목록에서 바로 하려면 해당
 * 워크스페이스로 들어갔다 나와야 했는데, 지우려는 워크스페이스에 먼저 들어가야 하는 건
 * 순서가 거꾸로다. 여기서는 화면 이동 없이 목록에서 처리한다.
 *
 * 동작 자체는 기존 것을 그대로 쓴다(DeleteWorkspaceModal, useRenameWorkspace) — 삭제
 * 확인 절차(영향 요약 + 이름 입력)를 여기서 다시 만들면 두 입구의 안전장치가 갈린다.
 *
 * 표시 규칙:
 *  - OWNER 에게만 보인다. 서버가 MEMBER 의 이름 변경·삭제를 403 으로 막으므로, 보여 주고
 *    실패시키는 대신 아예 감춘다(호출부에서 role 로 걸러 이 컴포넌트를 안 그린다)
 *  - 평소엔 투명하고 행에 호버·포커스했을 때, 또는 메뉴가 열려 있을 때 보인다
 *  - 터치 기기엔 호버가 없어서 항상 보인다(pointer-coarse)
 */
const WorkspaceActionsMenu = ({ workspace }: WorkspaceActionsMenuProps) => {
  const [renameOpen, setRenameOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  return (
    <>
      <Dropdown
        renderTrigger={(toggle, open) => (
          <button
            type="button"
            aria-label={`${workspace.name} 설정`}
            onClick={toggle}
            className={classNames(
              'grid h-7 w-7 cursor-pointer place-items-center rounded-md text-text-3',
              'transition-[color,background-color,opacity] hover:bg-surface-3 hover:text-text-1',
              '[&>svg]:h-4 [&>svg]:w-4',
              // 평소엔 숨는다. 호버·포커스·메뉴 열림·터치 기기에서 드러난다
              'opacity-0 focus-visible:opacity-100 group-hover/nav-item:opacity-100 pointer-coarse:opacity-100',
              open && 'bg-surface-3 text-text-1 opacity-100',
            )}
          >
            <DotsHorizontalIcon />
          </button>
        )}
      >
        {(close) => (
          <>
            <MenuItem
              icon={<PencilIcon />}
              onClick={() => {
                setRenameOpen(true);
                close();
              }}
            >
              이름 변경
            </MenuItem>
            <MenuItem
              danger
              icon={<TrashIcon />}
              onClick={() => {
                setDeleteOpen(true);
                close();
              }}
            >
              삭제
            </MenuItem>
          </>
        )}
      </Dropdown>

      <RenameWorkspaceModal
        workspaceId={workspace.id}
        currentName={workspace.name}
        open={renameOpen}
        onClose={() => setRenameOpen(false)}
      />
      {/* 삭제 성공 시 모달이 스스로 목록을 갱신하고 /home 으로 보낸다 —
          지금 보고 있던 워크스페이스를 지웠을 수도 있어서 그 처리가 필요하다 */}
      <DeleteWorkspaceModal
        workspaceId={workspace.id}
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
      />
    </>
  );
};

export default WorkspaceActionsMenu;
