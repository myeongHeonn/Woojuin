import { useNavigate, useParams } from 'react-router-dom';
import GlassButton from '@/components/ui/button/GlassButton';
import { TrashIcon } from '@/assets/icons';

/**
 * 스테이지 헤더의 휴지통 버튼 — 이 워크스페이스의 휴지통(/workspace/:id/trash)으로 간다.
 * 휴지통은 워크스페이스별이라 전역 사이드바가 아니라 여기(헤더)에서 진입한다.
 */
const TrashButton = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();

  return (
    <GlassButton
      icon={<TrashIcon />}
      aria-label="휴지통"
      onClick={() => navigate(`/workspace/${workspaceId}/trash`)}
    />
  );
};

export default TrashButton;
