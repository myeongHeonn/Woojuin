import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchMembers, createInvitation, removeMember } from '@/services/workspaces';

/** 워크스페이스 멤버 목록 — 공유 모달·아바타 스택이 쓰는 서버 상태 */
export function useMembers(workspaceId: number) {
  return useQuery({
    queryKey: ['members', workspaceId],
    queryFn: () => fetchMembers(workspaceId),
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
    },
  });
}
