import { classNames } from '@/utils/classNames';

/** 도는 로딩 스피너 — 크기는 넘겨받는 className 으로 조절한다 */
const Spinner = ({ className }: { className?: string }) => (
  <span
    role="status"
    aria-label="로딩 중"
    className={classNames(
      'inline-block animate-spin rounded-full border-2 border-white/15 border-t-accent',
      className,
    )}
  />
);

export default Spinner;
