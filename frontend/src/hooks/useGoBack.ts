import { useNavigate } from 'react-router-dom';

/**
 * 화면 안의 "뒤로" 버튼 동작 — **히스토리를 쓰지 않고 갈 곳을 직접 지정한다.**
 *
 * 앱이 히스토리 항목을 늘리지 않기 때문이다(utils/singleHistoryEntry). 뒤가 비어 있으니
 * `navigate(-1)` 은 뒤로 가는 게 아니라 앱을 벗어난다 — 화면 안의 버튼이 앱을 닫아 버리면
 * 안 되므로 목적지를 명시한다.
 *
 * 컴포넌트가 아니라 훅인 이유: 이 판단을 쓰는 자리들의 **모양이 다르다**
 * (BackButton 은 40×40 아이콘, 에러 화면은 라벨 붙은 알약형). 동작만 여기 모아 두면
 * 껍데기는 각자 만들면서도 판단이 갈리지 않는다.
 */
export const useGoBack = (fallback = '/'): (() => void) => {
  const navigate = useNavigate();

  return () => {
    navigate(fallback);
  };
};
