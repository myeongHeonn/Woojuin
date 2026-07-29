import { useWorkspaces } from '@/hooks/useWorkspaces';

/**
 * 사이드바가 쓰는 워크스페이스 분할 — "무엇이 개인 스페이스인가" 를 정하는 유일한 곳.
 *
 * FixedNav 는 개인을 그리고 WorkspaceNav 는 같은 것을 빼야 하는데,
 * 두 파일이 각자 판단하면 규칙이 어긋나는 순간 사이드바에 같은 워크스페이스가
 * 두 번 뜨거나 아예 사라진다. 그것도 조용히. 그래서 판단을 여기로 모은다.
 *
 * 판별 기준은 서버가 주는 type(PERSONAL/TEAM) 하나다 — 개인 스페이스 id 도 목록에서
 * 유도한다(임시 personalSpaceId 에 기대지 않는다). 목록이 오기 전엔 personalSpaceId 가
 * undefined 이므로, 소비처는 isLoading 을 보고 리다이렉트/링크를 미뤄 "/workspace/undefined"
 * 로 새는 걸 막는다.
 */
export function useSpaces() {
  const { data: workspaces = [], isLoading } = useWorkspaces();

  return {
    personalSpaceId: workspaces.find((workspace) => workspace.type === 'PERSONAL')?.id,
    /** 개인을 뺀 나머지 — 사이드바 Workspaces 목록에 들어간다 */
    teams: workspaces.filter((workspace) => workspace.type === 'TEAM'),
    isLoading,
  };
}
