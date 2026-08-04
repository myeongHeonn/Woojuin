import { useQuery } from '@tanstack/react-query';
import { fetchMyAiUsage } from '@/services/auth';

const SideBarAiUsage = () => {
  const {
    data: aiUsage,
    isPending,
    isError,
  } = useQuery({
    queryKey: ['user', 'me', 'ai-usage'],
    queryFn: fetchMyAiUsage,
  });

  const isLimited = Boolean(aiUsage?.limitEnabled && !aiUsage.unlimited);
  const usagePercent =
    isLimited && aiUsage && aiUsage.limit > 0
      ? Math.min(100, (aiUsage.used / aiUsage.limit) * 100)
      : 0;

  const usageValue = isPending
    ? '—'
    : isError
      ? '확인 불가'
      : aiUsage?.unlimited
        ? '무제한'
        : aiUsage?.limitEnabled
          ? `${aiUsage.used.toLocaleString('ko-KR')} / ${aiUsage.limit.toLocaleString('ko-KR')}회`
          : '제한 없음';

  return (
    <div className="px-1 pb-4" aria-labelledby="sidebar-ai-usage-title">
      <div className="mb-2 flex items-center justify-between gap-3">
        <span id="sidebar-ai-usage-title" className="text-xs font-medium text-text-3">
          이번 달 AI 사용량
        </span>
        <span className="shrink-0 text-xs font-semibold tabular-nums text-text-2">
          {usageValue}
        </span>
      </div>

      {isPending ? (
        <div className="h-1.5 animate-pulse rounded-pill bg-surface-3" aria-hidden="true" />
      ) : isLimited && aiUsage ? (
        <div
          className="h-1.5 overflow-hidden rounded-pill bg-surface-3"
          role="progressbar"
          aria-label="이번 달 AI 사용량"
          aria-valuemin={0}
          aria-valuemax={aiUsage.limit}
          aria-valuenow={Math.min(aiUsage.used, aiUsage.limit)}
        >
          <div
            className="h-full rounded-pill bg-accent transition-[width] duration-300"
            style={{ width: `${usagePercent}%` }}
          />
        </div>
      ) : (
        <div className="h-1.5 rounded-pill bg-surface-3" aria-hidden="true" />
      )}

      <p className="mt-2 text-[11px] leading-[1.45] text-text-3">
        {isError
          ? '사용량을 불러오지 못했어요. 잠시 후 다시 확인해 주세요.'
          : '아이템을 저장할 때마다 AI 사용량이 1회 차감돼요.'}
      </p>
    </div>
  );
};

export default SideBarAiUsage;
