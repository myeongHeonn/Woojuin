import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '@/hooks/useUser';
import { useMembers, useCreateInvitation, useRemoveMember } from '@/hooks/useWorkspaceMembers';
import Spinner from '@/components/ui/Spinner';
import MemberList from './MemberList';

interface ShareModalProps {
  workspaceId: number;
  /** 팝오버 닫기 (HeaderPopover 가 넘겨준다) */
  onClose: () => void;
}

/**
 * 공유 모달 — 링크 복사 + 멤버 목록. 헤더 공유 버튼 아래로 뜬다.
 * 내 역할에 따라 상단 동작이 갈린다: MEMBER 는 나가기(탈퇴), OWNER 는 각 멤버 내보내기(강퇴).
 * 역할은 멤버 목록에서 나(myUserId)를 찾아 판단한다.
 */
const ShareModal = ({ workspaceId, onClose }: ShareModalProps) => {
  const navigate = useNavigate();
  const { userId: myUserId } = useUser();
  const { data: members = [], isLoading } = useMembers(workspaceId);
  const invite = useCreateInvitation(workspaceId);
  const remove = useRemoveMember(workspaceId);
  const [copied, setCopied] = useState(false);

  const isOwner = members.find((m) => m.userId === myUserId)?.role === 'OWNER';

  const handleCopy = async () => {
    const inv = await invite.mutateAsync();
    await navigator.clipboard.writeText(`${window.location.origin}/invite/${inv.code}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  // 탈퇴 → 이 워크스페이스 접근 불가, 개인 홈으로 보낸다
  const handleLeave = () =>
    remove.mutate(myUserId, {
      onSuccess: () => {
        onClose();
        navigate('/home');
      },
    });

  return (
    <div className="w-[300px]">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-bold text-text-1">공유</h2>
        {/* 나가기는 MEMBER 만 — OWNER 는 마지막 소유자 문제로 탈퇴가 없다 */}
        {!isOwner && (
          <button
            type="button"
            onClick={handleLeave}
            className="text-xs text-text-3 transition-colors hover:text-danger"
          >
            나가기
          </button>
        )}
      </div>

      {/* 초대 링크 생성은 백엔드가 OWNER 만 허용(POST 시 403) — OWNER 에게만 보인다 */}
      {isOwner && (
        <button
          type="button"
          onClick={handleCopy}
          disabled={invite.isPending}
          className="mb-3 w-full rounded-lg bg-accent px-3 py-2 text-[13px] font-semibold text-white transition-colors hover:bg-accent-hover disabled:opacity-60"
        >
          {copied ? '링크가 복사됐어요' : '초대 링크 복사'}
        </button>
      )}

      <div className="border-t border-border-soft pt-1">
        {isLoading ? (
          <div className="grid place-items-center py-4">
            <Spinner className="h-5 w-5" />
          </div>
        ) : (
          <MemberList
            members={members}
            myUserId={myUserId}
            canKick={isOwner}
            onKick={(id) => remove.mutate(id)}
          />
        )}
      </div>
    </div>
  );
};

export default ShareModal;
