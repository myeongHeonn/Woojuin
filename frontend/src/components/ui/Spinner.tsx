import { classNames } from '@/utils/classNames';

/** 도는 로딩 스피너 — 크기는 넘겨받는 className 으로 조절한다 */
const Spinner = ({ className }: { className?: string }) => (
  <span
    role="status"
    aria-label="로딩 중"
    className={classNames(
      // 트랙(회전하지 않는 나머지 테두리)은 border 토큰을 쓴다 — 흰색 반투명이면 라이트에서
      // 밝은 배경에 묻혀 사라지고, 돌아가는 accent 호만 덩그러니 남는다
      'inline-block animate-spin rounded-full border-2 border-border border-t-accent',
      className,
    )}
  />
);

export default Spinner;
