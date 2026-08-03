interface TutorialReplayCardProps {
  personalSpaceId?: number;
  sharedWorkspaceId?: number;
  loading: boolean;
  onReplay: (type: 'PERSONAL' | 'SHARED_WORKSPACE', workspaceId: number) => void;
}

const TutorialReplayCard = ({
  personalSpaceId,
  sharedWorkspaceId,
  loading,
  onReplay,
}: TutorialReplayCardProps) => {
  const [open, setOpen] = useState(false);

  const replay = (type: 'PERSONAL' | 'SHARED_WORKSPACE', workspaceId: number) => {
    setOpen(false);
    onReplay(type, workspaceId);
  };

  return (
    <>
      <section className="rounded-[20px] border border-border-soft bg-surface p-1.5">
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="flex w-full items-center rounded-md px-5 py-[13px] text-left hover:bg-surface-2"
        >
          <span className="flex-1 text-sm font-semibold text-text-1">튜토리얼 다시 보기</span>
          <span aria-hidden="true" className="text-lg leading-none text-text-3">
            ›
          </span>
        </button>
      </section>

      <Modal open={open} onClose={() => setOpen(false)} title="튜토리얼 다시 보기">
        <p className="text-[13px] text-text-2">다시 확인할 튜토리얼을 선택해 주세요.</p>
        <div className="mt-4 grid grid-cols-2 gap-2">
          <button
            type="button"
            disabled={loading || personalSpaceId === undefined}
            onClick={() => personalSpaceId !== undefined && replay('PERSONAL', personalSpaceId)}
            className="rounded-lg border border-border px-3 py-3 text-xs font-semibold text-text-1 transition-colors enabled:hover:border-accent enabled:hover:text-accent disabled:cursor-not-allowed disabled:opacity-40"
          >
            개인 스페이스
          </button>
          <button
            type="button"
            disabled={loading || sharedWorkspaceId === undefined}
            onClick={() =>
              sharedWorkspaceId !== undefined && replay('SHARED_WORKSPACE', sharedWorkspaceId)
            }
            className="rounded-lg border border-border px-3 py-3 text-xs font-semibold text-text-1 transition-colors enabled:hover:border-accent enabled:hover:text-accent disabled:cursor-not-allowed disabled:opacity-40"
          >
            공유 워크스페이스
          </button>
        </div>
        {!loading && sharedWorkspaceId === undefined && (
          <p className="mt-2 text-[11px] text-text-3">참여 중인 공유 워크스페이스가 없어요.</p>
        )}
      </Modal>
    </>
  );
};

export default TutorialReplayCard;
import { useState } from 'react';
import Modal from '@/components/ui/Modal';
