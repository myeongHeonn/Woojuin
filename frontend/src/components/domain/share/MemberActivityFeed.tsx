import type { MemberActivity } from '@/services/workspaces';
import { formatRelativeDate } from '@/utils/formatDate';
import Avatar from '@/components/ui/Avatar';
import Spinner from '@/components/ui/Spinner';

interface MemberActivityFeedProps {
  activities: MemberActivity[];
  isLoading: boolean;
}

/** 활동 종류별 문구 — 닉네임은 행에서 따로 굵게 표시하므로 여기엔 안 넣는다 */
const ACTION_TEXT: Record<MemberActivity['type'], string> = {
  JOINED: '님이 참여했어요',
  LEFT: '님이 나갔어요',
  KICKED: '님이 추방됐어요',
};

/** 강퇴만 위험 색으로 강조한다 — 참여/탈퇴는 중립 정보다 */
const ACTION_TEXT_COLOR: Record<MemberActivity['type'], string> = {
  JOINED: 'text-text-2',
  LEFT: 'text-text-2',
  KICKED: 'text-danger',
};

/**
 * 워크스페이스 멤버 활동 피드(가입/탈퇴/추방) — 공유 모달에서 멤버 목록 아래에 붙는다.
 * 데이터/로딩만 받고 조회는 호출부(ShareModal)가 useMemberActivities로 한다 —
 * MemberList와 같은 자리에 있어 나란히 쓸 수 있게 얇게 둔다.
 */
const MemberActivityFeed = ({ activities, isLoading }: MemberActivityFeedProps) => {
  if (isLoading) {
    return (
      <div className="grid place-items-center py-4">
        <Spinner className="h-5 w-5" />
      </div>
    );
  }

  if (activities.length === 0) {
    return <p className="py-3 text-center text-[12px] text-text-3">아직 활동이 없어요</p>;
  }

  return (
    <ul className="flex max-h-40 flex-col overflow-y-auto">
      {activities.map((a, i) => (
        // 같은 유저가 참여→탈퇴→재참여를 반복할 수 있어 userId만으로는 키가 겹친다
        <li key={`${a.userId}-${a.occurredAt}-${i}`} className="flex items-center gap-2.5 py-1.5">
          <Avatar name={a.nickname} color={a.avatarColor} size={22} />
          <span className="min-w-0 flex-1 truncate text-[12px]">
            <span className="font-semibold text-text-1">{a.nickname}</span>
            <span className={ACTION_TEXT_COLOR[a.type]}>{ACTION_TEXT[a.type]}</span>
          </span>
          <span className="shrink-0 text-[11px] text-text-3">
            {formatRelativeDate(a.occurredAt)}
          </span>
        </li>
      ))}
    </ul>
  );
};

export default MemberActivityFeed;
