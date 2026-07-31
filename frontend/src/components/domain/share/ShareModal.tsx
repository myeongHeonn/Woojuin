import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '@/hooks/useUser';
import { useMembers, useCreateInvitation, useRemoveMember } from '@/hooks/useWorkspaceMembers';
import { useWorkspaces, useRenameWorkspace } from '@/hooks/useWorkspaces';
import Spinner from '@/components/ui/Spinner';
import TextInput from '@/components/ui/TextInput';
import IconButton from '@/components/ui/button/IconButton';
import { PencilIcon, CheckIcon } from '@/assets/icons';
import MemberList from './MemberList';

interface ShareModalProps {
  workspaceId: number;
  /** 팝오버 닫기 (HeaderPopover 가 넘겨준다) */
  onClose: () => void;
  /**
   * 워크스페이스 삭제 요청 — 모달은 팝오버 밖(ShareButton)에서 띄운다.
   * 팝오버 패널이 transform 으로 배치돼 안에서 fixed 모달을 열면 좌표가 틀어진다.
   */
  onRequestDelete: () => void;
}

/**
 * 워크스페이스 설정 모달 — 이름 변경 + 초대 링크 + 멤버 목록. 헤더 설정 버튼 아래로 뜬다.
 * 내 역할에 따라 갈린다: OWNER 는 이름 변경·초대·강퇴·삭제, MEMBER 는 목록 보기·나가기.
 * 역할은 멤버 목록에서 나(myUserId)를 찾아 판단한다.
 */
const ShareModal = ({ workspaceId, onClose, onRequestDelete }: ShareModalProps) => {
  const navigate = useNavigate();
  const { userId: myUserId } = useUser();
  const { data: members = [], isLoading } = useMembers(workspaceId);
  const { data: workspaces } = useWorkspaces();
  const invite = useCreateInvitation(workspaceId);
  const remove = useRemoveMember(workspaceId);
  const rename = useRenameWorkspace(workspaceId);
  const [copied, setCopied] = useState(false);
  // null 이면 보기 모드 — 편집을 열 때 현재 이름으로 채운다 (CategoryManage 의 편집 행과 같은 방식)
  const [editingName, setEditingName] = useState<string | null>(null);

  const isOwner = members.find((m) => m.userId === myUserId)?.role === 'OWNER';
  const name = workspaces?.find((workspace) => workspace.id === workspaceId)?.name ?? '';

  const handleCopy = async () => {
    const inv = await invite.mutateAsync();
    await navigator.clipboard.writeText(`${window.location.origin}/invite/${inv.code}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const handleRename = () => {
    const next = editingName?.trim();
    if (!next || next === name) {
      setEditingName(null);
      return;
    }
    rename.mutate(next, { onSuccess: () => setEditingName(null) });
  };

  // 탈퇴 → 이 워크스페이스 접근 불가, 개인 홈으로 보낸다.
  // 내 id 를 아직 모르면(프로필 로딩 중) 아무것도 하지 않는다 — 잘못된 대상을 내보내는 것보다
  // 한 번 더 누르게 하는 편이 낫다. 버튼도 그 동안 비활성이다.
  const handleLeave = () => {
    if (myUserId === undefined) return;
    remove.mutate(myUserId, {
      onSuccess: () => {
        onClose();
        navigate('/home');
      },
    });
  };

  return (
    <div className="w-[300px]">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-bold text-text-1">워크스페이스 설정</h2>
        {/* 역할별 파괴 액션 — MEMBER 는 나가기(OWNER 는 마지막 소유자 문제로 탈퇴가 없다),
            OWNER 는 워크스페이스 삭제. 같은 자리에 하나만 뜬다 */}
        {isOwner ? (
          <button
            type="button"
            onClick={onRequestDelete}
            className="text-xs text-text-3 transition-colors hover:text-danger"
          >
            삭제하기
          </button>
        ) : (
          <button
            type="button"
            onClick={handleLeave}
            disabled={myUserId === undefined}
            className="text-xs text-text-3 transition-colors hover:text-danger disabled:cursor-not-allowed disabled:opacity-50"
          >
            나가기
          </button>
        )}
      </div>

      {/* 이름 — OWNER 만 연필로 인라인 수정. MEMBER 에겐 읽기 전용 표시 */}
      {editingName !== null ? (
        <form
          className="mb-3 flex items-center gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            handleRename();
          }}
        >
          <TextInput
            autoFocus
            value={editingName}
            onChange={(e) => setEditingName(e.target.value)}
            aria-label="워크스페이스 이름"
            className="min-w-0 flex-1"
          />
          <IconButton label="저장" onClick={handleRename}>
            <CheckIcon className="h-4 w-4" />
          </IconButton>
          <IconButton label="취소" onClick={() => setEditingName(null)}>
            ✕
          </IconButton>
        </form>
      ) : (
        <div className="mb-3 flex items-center gap-2">
          <span className="min-w-0 flex-1 truncate text-sm text-text-1">{name}</span>
          {isOwner && (
            <IconButton label="이름 변경" onClick={() => setEditingName(name)}>
              <PencilIcon className="h-4 w-4" />
            </IconButton>
          )}
        </div>
      )}

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
