import { Outlet, useParams } from 'react-router-dom';
import { useWorkspaceEvents } from '@/hooks/useWorkspaceEvents';

/**
 * 워크스페이스 셸 — 화면을 그리지 않고 변경 신호(SSE) 구독만 맡는다.
 *
 * StageLayout(성좌·대시보드·지도·캔버스)이 아니라 이 자리에 두는 이유: 휴지통이
 * StageLayout 바깥이라 거기서 구독하면 휴지통만 신호를 못 받는다. 워크스페이스 라우트
 * 전체를 감싸면 연결 하나로 하위 화면을 모두 덮고, 뷰를 옮겨도 연결이 끊기지 않는다.
 */
const WorkspaceLayout = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();

  useWorkspaceEvents(Number(workspaceId));

  return <Outlet />;
};

export default WorkspaceLayout;
