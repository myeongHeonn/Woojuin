import { useState } from 'react';
import GlassButton from '@/components/ui/button/GlassButton';
import HeaderPopover from '@/components/ui/HeaderPopover';
import { SettingsIcon } from '@/assets/icons';
import ShareModal from './ShareModal';
import DeleteWorkspaceModal from './DeleteWorkspaceModal';

/**
 * 헤더 워크스페이스 설정 버튼 — 누르면 아래로 설정 모달(이름 변경 + 초대 + 멤버)이 뜬다.
 * 공유(사람+) 아이콘이었지만 이름 변경·삭제까지 들어오며 톱니로 바꿨다.
 * 여닫기·바깥클릭·ESC 는 HeaderPopover, 내용은 ShareModal 이 맡는다.
 * 삭제 모달은 팝오버의 형제로 둔다 — 패널이 transform 으로 배치돼 안에서
 * fixed 모달을 열면 좌표가 틀어진다(WorkspaceSwitcher 의 생성 모달과 같은 패턴).
 */
const ShareButton = ({ workspaceId }: { workspaceId: number }) => {
  const [deleteOpen, setDeleteOpen] = useState(false);

  return (
    <>
      <div data-tutorial="workspace-share">
        <HeaderPopover
          trigger={<GlassButton icon={<SettingsIcon />} aria-label="워크스페이스 설정" />}
        >
          {(close) => (
            <ShareModal
              workspaceId={workspaceId}
              onClose={close}
              onRequestDelete={() => {
                close();
                setDeleteOpen(true);
              }}
            />
          )}
        </HeaderPopover>
      </div>

      <DeleteWorkspaceModal
        workspaceId={workspaceId}
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
      />
    </>
  );
};

export default ShareButton;
