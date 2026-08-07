import { Link } from 'react-router-dom';
import ErrorScreen from '@/components/domain/error/ErrorScreen';
import { errorPrimaryActionClass } from '@/components/domain/error/errorActionStyles';

/**
 * 없는 주소로 들어왔을 때 — 라우터의 `*` 가 여기로 보낸다.
 *
 * 이 화면이 없으면 React Router 의 기본 화면(흰 배경 + 영문 "404 Not Found")이 그대로
 * 노출된다. 오타·바뀐 주소·죽은 공유 링크가 실제로 가장 흔한 경우다.
 *
 * 홈을 `/` 로 두는 이유: 로그인 상태를 모른다. `/home` 은 AuthLayout 이 지키고 있어서
 * 비로그인이면 곧바로 `/` 로 튕기므로, 랜딩으로 보내면 두 경우가 한 번에 맞는다.
 *
 * "이전 화면으로"를 본문에 두지 않는 이유: 뒤로가기는 ErrorScreen 이 좌상단에 항상 두고
 * 있고(히스토리가 없으면 스스로 홈으로 폴백한다), 본문에 또 두면 같은 화면에 뒤로 가는
 * 버튼이 둘이 된다.
 */
const NotFoundPage = () => (
  <ErrorScreen
    code="404"
    // 바로 위에 로고의 궤도가 그려져 있으니 그걸 받는다 — 길을 잃었다는 뜻이 우주 컨셉과
    // 맞고, "찾을 수 없습니다" 같은 상태 보고보다 읽는 사람 편이 된다
    title="궤도를 벗어났어요"
    description="이 좌표엔 아무것도 없어요. 주소가 바뀌었거나 지워진 화면일 수 있어요."
    actions={
      <Link to="/" className={errorPrimaryActionClass}>
        홈으로 돌아가기
      </Link>
    }
  />
);

export default NotFoundPage;
