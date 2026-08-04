import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { useWorkspaceEvents } from '@/hooks/useWorkspaceEvents';
import { useWorkspaceEvictionGuard } from '@/hooks/useWorkspaceEvictionGuard';
import { useWorkspacePreview } from '@/hooks/useWorkspaces';
import ConfirmModal from '@/components/ui/ConfirmModal';

/**
 * 워크스페이스 셸 — 화면을 그리지 않고 변경 신호(SSE) 구독 + 추방 감지를 맡는다.
 *
 * StageLayout(성좌·대시보드·지도·캔버스)이 아니라 이 자리에 두는 이유: 휴지통이
 * StageLayout 바깥이라 거기서 구독하면 휴지통만 신호를 못 받는다. 워크스페이스 라우트
 * 전체를 감싸면 연결 하나로 하위 화면을 모두 덮고, 뷰를 옮겨도 연결이 끊기지 않는다.
 *
 * 추방 감지: 이미 이 워크스페이스 화면에 있던 사람이 강퇴당하면, 그 사실을 알 방법이
 * 없어 API 호출들이 조용히 403으로 깨지기만 했다(S15P11C105-435). member SSE 신호로
 * 멤버 목록이 다시 로드된 뒤 내가 그 안에 없으면 확인 모달을 띄우고 /home으로 보낸다.
 *
 * 같은 403이라도 초대 링크 없이 남의 워크스페이스 URL로 바로 들어온 경우엔 "추방"이
 * 아니라 "권한 없음"으로 안내한다(구분 로직은 useWorkspaceEvictionGuard 참고).
 */
const WorkspaceLayout = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();
  const id = Number(workspaceId);

  useWorkspaceEvents(id);
  const { evicted, accessDenied } = useWorkspaceEvictionGuard(id);
  // 멤버가 아니어서 뜨는 모달이니 이름은 별도(멤버십 무관) 미리보기 API로 가져온다.
  // 로딩 중이라 아직 이름이 없으면 워크스페이스라는 일반 표현으로 대체한다.
  const { data: preview } = useWorkspacePreview(id, evicted || accessDenied);
  const workspaceLabel = preview?.name ? `'${preview.name}'` : '워크스페이스';

  return (
    <>
      <Outlet />
      <ConfirmModal
        open={evicted}
        title={`${workspaceLabel}에서 추방되었어요`}
        description="이 워크스페이스에 더 이상 접근할 수 없어요."
        confirmLabel="확인"
        showCancel={false}
        onConfirm={() => navigate('/home')}
        onCancel={() => navigate('/home')}
      />
      <ConfirmModal
        open={accessDenied}
        title={`${workspaceLabel}에 접근 권한이 없어요`}
        description="이 워크스페이스에 접근할 권한이 없어요."
        confirmLabel="확인"
        showCancel={false}
        onConfirm={() => navigate('/home')}
        onCancel={() => navigate('/home')}
      />
    </>
  );
};

export default WorkspaceLayout;
