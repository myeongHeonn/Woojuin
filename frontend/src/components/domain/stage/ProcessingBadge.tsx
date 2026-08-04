import { classNames } from '@/utils/classNames';
import Spinner from '@/components/ui/Spinner';

interface ProcessingBadgeProps {
  /** 처리 중인 아이템 개수 — 0이면 렌더하지 않는다(호출부가 조건 분기 안 해도 되게) */
  count: number;
  /** "별 만드는 중" · "장소 찾는 중" 처럼 뷰마다 다른 동사 */
  label: string;
  className?: string;
}

/**
 * 성좌·지도 뷰 공용 — 저장한 아이템이 처리 중(PROCESSING)이라 아직 안 보일 때
 * "왜 안 보이지?"를 없애는 배지. 폴링 신호(processingPollInterval)는 이미 있었고
 * 그동안 화면에 아무 표시도 안 했던 것이 문제였다 — 표시만 이어붙인다.
 */
const ProcessingBadge = ({ count, label, className }: ProcessingBadgeProps) => {
  if (count === 0) return null;

  return (
    <div
      className={classNames(
        'inline-flex items-center gap-2 rounded-pill border border-border bg-surface/95 px-3.5 py-[7px] text-xs text-text-1 shadow-float backdrop-blur-xl',
        className,
      )}
    >
      <Spinner className="h-[13px] w-[13px]" />
      {label} · {count}개
    </div>
  );
};

export default ProcessingBadge;
