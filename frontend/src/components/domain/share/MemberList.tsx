import type { WorkspaceMember } from '@/services/workspaces';
import Avatar from '@/components/ui/Avatar';

interface MemberListProps {
  members: WorkspaceMember[];
  /** 현재 로그인 사용자 — "나" 표시 + 자기 자신엔 강퇴 버튼을 숨긴다 */
  myUserId: number;
  /** 내가 OWNER 라 강퇴할 수 있는가. true 일 때만 다른 멤버 행에 내보내기가 뜬다 */
  canKick?: boolean;
  onKick?: (userId: number) => void;
}

/**
 * 워크스페이스 멤버 목록 — 아바타 + 이름 + 역할. 공유 모달과 아바타 스택 팝오버가 공유한다.
 * 강퇴(내보내기)는 canKick(내가 OWNER)일 때, 그리고 자기 자신이 아닐 때만 보인다.
 */
const MemberList = ({ members, myUserId, canKick, onKick }: MemberListProps) => (
  <ul className="flex flex-col">
    {members.map((m) => {
      const isMe = m.userId === myUserId;
      return (
        <li key={m.userId} className="flex items-center gap-2.5 py-2">
          <Avatar userId={m.userId} name={m.nickname} />
          <span className="min-w-0 flex-1 truncate text-[13px] text-text-1">
            {m.nickname}
            {isMe && <span className="ml-1 text-text-3">(나)</span>}
          </span>

          <span className="shrink-0 text-[11px] font-semibold text-text-3">{m.role}</span>

          {canKick && !isMe && (
            <button
              type="button"
              aria-label={`${m.nickname} 내보내기`}
              onClick={() => onKick?.(m.userId)}
              className="shrink-0 text-[11px] text-text-3 hover:text-danger"
            >
              내보내기
            </button>
          )}
        </li>
      );
    })}
  </ul>
);

export default MemberList;
