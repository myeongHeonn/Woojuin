import type { ReactNode } from 'react';
import { BellIcon } from '@/assets/icons';
import type { AiUsage } from '@/services/auth';

interface SettingsCardProps {
  notificationEnabled: boolean;
  onNotificationToggle: () => void;
  aiUsage?: AiUsage;
  aiUsageLoading: boolean;
  aiUsageError: boolean;
  onHelp: () => void;
}

const RowIcon = ({ children }: { children: ReactNode }) => (
  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] bg-surface-3 text-text-2 [&>svg]:h-4 [&>svg]:w-4">
    {children}
  </span>
);

const SettingsCard = ({
  notificationEnabled,
  onNotificationToggle,
  aiUsage,
  aiUsageLoading,
  aiUsageError,
  onHelp,
}: SettingsCardProps) => {
  const usagePercent =
    aiUsage && aiUsage.limitEnabled && !aiUsage.unlimited && aiUsage.limit > 0
      ? Math.min(100, (aiUsage.used / aiUsage.limit) * 100)
      : 0;
  const usageDescription = aiUsageError
    ? '사용량을 불러오지 못했습니다.'
    : aiUsageLoading
      ? '사용량을 불러오는 중입니다.'
      : aiUsage?.unlimited
        ? 'AI 정리를 제한 없이 사용할 수 있어요.'
        : aiUsage?.limitEnabled
          ? `월 ${aiUsage.limit.toLocaleString('ko-KR')}회 중 ${aiUsage.used.toLocaleString('ko-KR')}회를 사용했어요.`
          : '현재 월간 사용량 제한이 적용되지 않아요.';

  return (
    <>
      <section className="mb-4 rounded-[20px] border border-border-soft bg-surface p-1.5">
        <div className="flex items-center gap-3.5 rounded-md px-5 py-[13px] hover:bg-surface-2">
          <RowIcon>
            <BellIcon />
          </RowIcon>
          <div className="min-w-0 flex-1">
            <h2 className="text-sm font-semibold text-text-1">알림</h2>
            <p className="mt-0.5 text-xs text-text-3">AI 정리가 완료되면 알려드려요</p>
          </div>
          <button
            type="button"
            role="switch"
            aria-label="완료 알림"
            aria-checked={notificationEnabled}
            onClick={onNotificationToggle}
            className={[
              'relative h-6 w-[42px] shrink-0 rounded-pill transition-colors',
              notificationEnabled ? 'bg-accent' : 'bg-surface-3',
            ].join(' ')}
          >
            <span
              className={[
                'absolute top-[3px] h-[18px] w-[18px] rounded-full transition-[left,background-color]',
                notificationEnabled ? 'left-[21px] bg-white' : 'left-[3px] bg-text-2',
              ].join(' ')}
            />
          </button>
        </div>
      </section>

      <section
        aria-labelledby="monthly-ai-usage-title"
        className="mb-4 rounded-[20px] border border-border-soft bg-surface px-5 py-[18px]"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2
              id="monthly-ai-usage-title"
              className="text-xs font-bold tracking-[0.1em] text-text-3"
            >
              이번 달 AI 사용량
            </h2>
            <p className="mt-2 text-xs text-text-3">{usageDescription}</p>
          </div>
          <div className="shrink-0 text-right">
            <span className="text-[24px] font-extrabold text-text-1">
              {aiUsage ? aiUsage.used.toLocaleString('ko-KR') : '—'}
            </span>
            <span className="ml-1 text-xs font-semibold text-text-3">회</span>
          </div>
        </div>

        {aiUsage?.limitEnabled && !aiUsage.unlimited && (
          <div className="mt-4">
            <div
              role="progressbar"
              aria-label="월간 AI 사용량"
              aria-valuemin={0}
              aria-valuemax={aiUsage.limit}
              aria-valuenow={Math.min(aiUsage.used, aiUsage.limit)}
              className="h-2 overflow-hidden rounded-pill bg-surface-3"
            >
              <div
                className="h-full rounded-pill bg-accent transition-[width]"
                style={{ width: `${usagePercent}%` }}
              />
            </div>
            <div className="mt-1.5 flex justify-between text-[11px] text-text-3">
              <span>{aiUsage.used.toLocaleString('ko-KR')}회 사용</span>
              <span>{aiUsage.limit.toLocaleString('ko-KR')}회 한도</span>
            </div>
          </div>
        )}

        {aiUsage?.unlimited && (
          <span className="mt-3 inline-flex rounded-pill bg-surface-3 px-2.5 py-1 text-[11px] font-bold text-text-2">
            무제한
          </span>
        )}
      </section>

      <section className="rounded-[20px] border border-border-soft bg-surface p-1.5">
        <button
          type="button"
          onClick={onHelp}
          className="flex w-full items-center gap-3.5 rounded-md px-5 py-[13px] text-left hover:bg-surface-2"
        >
          <span className="flex-1 text-sm font-semibold text-text-1">도움말 및 고객센터</span>
        </button>
      </section>
    </>
  );
};

export default SettingsCard;
