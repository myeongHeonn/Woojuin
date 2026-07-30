import { Navigate } from 'react-router-dom';
import { useSpaces } from '@/hooks/useSpaces';
import Spinner from '@/components/ui/Spinner';

/**
 * 로그인 직후 도착지 — 그림 없는 리다이렉트 전용 화면.
 *
 * 개인 워크스페이스 id 는 가입할 때 서버가 만들어주므로 URL 을 고정할 수 없다.
 * 워크스페이스 목록에서 type === 'PERSONAL' 인 것을 찾아 그 우주로 넘긴다.
 * 목록이 오기 전엔 id 를 모르므로 스피너로 기다린다("/workspace/undefined" 방지).
 *
 * replace 인 이유: push 면 성좌에서 뒤로가기 → /home → 다시 성좌로 튕겨서
 * 뒤로가기가 영영 안 먹는다.
 */
const PersonalSpacePage = () => {
  const { personalSpaceId } = useSpaces();

  if (!personalSpaceId) {
    return (
      <div className="grid min-h-screen place-items-center bg-space">
        <Spinner className="h-6 w-6" />
      </div>
    );
  }

  return <Navigate to={`/workspace/${personalSpaceId}/universe`} replace />;
};

export default PersonalSpacePage;
