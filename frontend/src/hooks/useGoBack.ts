import { useLocation, useNavigate } from 'react-router-dom';

/**
 * "뒤로 가기" 동작 — 히스토리가 있으면 한 칸 뒤로, 없으면 fallback 으로.
 *
 * `navigate(-1)` 만 쓰면 안 되는 이유: 링크를 직접 열었거나 설치된 PWA 로 바로 진입한
 * 경우엔 앱 밖으로 나가버리거나 아무 일도 일어나지 않는다.
 * `location.key` 가 'default' 면 라우터 기준 첫 화면이라는 뜻이다.
 *
 * 뷰 전환은 히스토리를 쌓지 않지만(utils/installedApp) 그 밖의 이동은 그대로 쌓이므로,
 * 여기서는 실제 히스토리를 쓰는 게 맞다 — 가입 화면에서 개인정보처리방침을 열었다면
 * 뒤로가기로 가입 화면에 돌아가야 한다. 뷰에서 눌렀다면 되돌아갈 뒤가 없어 fallback 으로 간다.
 *
 * 컴포넌트가 아니라 훅인 이유: 이 판단을 쓰는 자리들의 **모양이 다르다**
 * (BackButton 은 40×40 아이콘, 에러 화면은 라벨 붙은 알약형). 동작만 여기 모아 두면
 * 껍데기는 각자 만들면서도 판단이 갈리지 않는다.
 */
export const useGoBack = (fallback = '/'): (() => void) => {
  const navigate = useNavigate();
  const location = useLocation();
  const isFirstEntry = location.key === 'default';

  return () => {
    if (isFirstEntry) {
      navigate(fallback);
      return;
    }
    navigate(-1);
  };
};
