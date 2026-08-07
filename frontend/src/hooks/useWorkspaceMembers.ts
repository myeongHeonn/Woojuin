import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchMembers,
  createInvitation,
  removeMember,
  fetchMemberActivities,
} from '@/services/workspaces';

/**
 * 워크스페이스 멤버 목록 — 공유 모달·아바타 스택이 쓰는 서버 상태.
 *
 * refetchOnWindowFocus를 'always'로 강제한다(기본 staleTime=30s보다 우선). 탭이
 * 숨겨지면 SSE 연결이 끊기는데(useWorkspaceEvents), 그 사이 남이 가입/탈퇴/추방돼도
 * 30초 안에 돌아오면 focus 재요청이 "아직 신선하다"며 건너뛰어 새로고침 전까진 낡은
 * 목록이 계속 보였다.
 */
export function useMembers(workspaceId: number) {
  return useQuery({
    queryKey: ['members', workspaceId],
    queryFn: () => fetchMembers(workspaceId),
    refetchOnWindowFocus: 'always',
  });
}

/** 초대 링크 코드 발급 — 누를 때만 호출(useMutation). 응답 code 로 링크를 만든다 */
export function useCreateInvitation(workspaceId: number) {
  return useMutation({
    mutationFn: () => createInvitation(workspaceId),
  });
}

/**
 * 멤버 내보내기 — 강퇴(OWNER→타인)와 탈퇴(본인)를 서버가 userId 로 구분한다.
 * 성공 시 멤버 목록을 갱신하고, 탈퇴면 내 워크스페이스 목록도 바뀌므로 함께 무효화한다.
 */
export function useRemoveMember(workspaceId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: number) => removeMember(workspaceId, userId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['members', workspaceId] });
      qc.invalidateQueries({ queryKey: ['workspaces'] });
      qc.invalidateQueries({ queryKey: ['member-activities', workspaceId] });
    },
  });
}

/**
 * 멤버 활동 이력(가입/탈퇴/추방) — 최신순 피드.
 * refetchOnWindowFocus를 'always'로 두는 이유는 useMembers와 같다(SSE 재연결 사이 공백 보정).
 */
export function useMemberActivities(workspaceId: number) {
  return useQuery({
    queryKey: ['member-activities', workspaceId],
    queryFn: () => fetchMemberActivities(workspaceId),
    refetchOnWindowFocus: 'always',
  });
}
