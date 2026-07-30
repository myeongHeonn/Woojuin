import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchInvitation, acceptInvitation } from '@/services/workspaces';

/** 초대 정보 — 코드로 워크스페이스 이름 등을 미리 본다. 만료/무효면 에러(재시도 안 함) */
export function useInvitation(code: string) {
  return useQuery({
    queryKey: ['invitation', code],
    queryFn: () => fetchInvitation(code),
    enabled: code.length > 0,
    retry: false,
  });
}

/** 초대 수락 — 성공하면 내 워크스페이스 목록에 새로 뜨도록 무효화한다 */
export function useAcceptInvitation(code: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => acceptInvitation(code),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['workspaces'] }),
  });
}
