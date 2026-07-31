import { useState } from 'react';
import { useUser } from '@/hooks/useUser';
import { useMembers } from '@/hooks/useWorkspaceMembers';
import Avatar from '@/components/ui/Avatar';
import MemberList from './MemberList';

/**
 * 멤버 아바타 스택(페이스파일) — 최대 3명 + 초과 시 +N.
 * hover 또는 click 하면 아래로 멤버 목록(읽기 전용)이 뜬다 — 공유 모달과 별개의 가벼운 목록.
 */
const MemberStack = ({ workspaceId }: { workspaceId: number }) => {
  const { userId: myUserId } = useUser();
  const { data: members = [] } = useMembers(workspaceId);
  const [open, setOpen] = useState(false);

  if (members.length === 0) return null;

  const shown = members.slice(0, 3);
  const extra = members.length - shown.length;

  return (
    <div
      data-tutorial="workspace-members"
      className="relative"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        aria-label="멤버 보기"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center -space-x-2"
      >
        {shown.map((m) => (
          <Avatar
            key={m.userId}
            userId={m.userId}
            name={m.nickname}
            className="ring-2 ring-space"
          />
        ))}
        {extra > 0 && (
          <span className="grid h-7 w-7 place-items-center rounded-full bg-surface-3 text-[11px] font-semibold text-text-2 ring-2 ring-space">
            +{extra}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-[calc(100%+8px)] z-50 w-[220px] rounded-lg border border-border bg-surface p-3 shadow-float">
          <MemberList members={members} myUserId={myUserId} />
        </div>
      )}
    </div>
  );
};

export default MemberStack;
