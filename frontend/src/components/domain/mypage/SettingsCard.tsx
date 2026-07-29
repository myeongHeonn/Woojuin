import type { ReactNode } from 'react';
import { BellIcon } from '@/assets/icons';

interface SettingsCardProps {
  notificationEnabled: boolean;
  onNotificationToggle: () => void;
  onUpgrade: () => void;
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
  onUpgrade,
  onHelp,
}: SettingsCardProps) => (
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

    <section className="mb-4 rounded-[20px] border border-border-soft bg-surface p-1.5">
      <h2 className="px-5 pb-1.5 pt-3.5 text-xs font-bold tracking-[0.1em] text-text-3">플랜</h2>
      <div className="flex items-center gap-3.5 px-5 py-4">
        <span className="rounded-pill bg-surface-3 px-2.5 py-1 text-[11px] font-extrabold tracking-[0.08em] text-text-2">
          FREE
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-bold text-text-1">무료 플랜</h3>
          <p className="mt-0.5 text-xs text-text-3">기본 저장 공간과 AI 정리 기능</p>
        </div>
        <button
          type="button"
          onClick={onUpgrade}
          className="shrink-0 rounded-[10px] border border-accent bg-accent px-3.5 py-[7px] text-[12.5px] font-bold text-white hover:bg-accent-hover"
        >
          Pro 업그레이드
        </button>
      </div>
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

export default SettingsCard;
