import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import Modal from '@/components/ui/Modal';
import { issueChatLinkCode } from '@/services/integrations';

interface ChatIntegrationCardProps {
  onError: () => void;
}

const ChatIntegrationCard = ({ onError }: ChatIntegrationCardProps) => {
  const discordApplicationId = import.meta.env.VITE_DISCORD_APPLICATION_ID;
  const discordInstallUrl = discordApplicationId
    ? `https://discord.com/oauth2/authorize?client_id=${encodeURIComponent(discordApplicationId)}`
    : null;
  const [open, setOpen] = useState(false);
  const mutation = useMutation({ mutationFn: issueChatLinkCode, onError });

  const copyCommand = async (command: string) => {
    await navigator.clipboard.writeText(command);
  };

  return (
    <>
      {/* 연결된 앱 섹션의 한 행 — 상자 없이 호버 필로만 눌림을 알린다 */}
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex w-full items-center rounded-[10px] px-2 py-3 text-left hover:bg-surface-2"
      >
        <span className="flex-1 text-sm font-semibold text-text-1">채팅 앱 연동</span>
        <span aria-hidden="true" className="text-lg leading-none text-text-3">
          ›
        </span>
      </button>

      <Modal open={open} onClose={() => setOpen(false)} title="Mattermost · Discord 연동">
        <p className="text-[13px] leading-relaxed text-text-2">
          최초 연결할 때만 일회용 코드가 필요합니다. 연결 후에는 다시 발급하지 않고 바로 저장할 수
          있습니다.
        </p>
        <div className="mt-3 rounded-[14px] bg-surface-2 px-4 py-3 text-xs leading-5 text-text-3">
          코드는 10분 동안 유효하며 한 번 사용하면 폐기됩니다.
        </div>

        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate()}
          className="mt-4 w-full rounded-lg bg-accent px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-accent-hover disabled:cursor-wait disabled:opacity-50"
        >
          {mutation.isPending ? '발급 중…' : mutation.data ? '새 코드 발급' : '연결 코드 발급'}
        </button>

        {mutation.data && (
          <div className="mt-4 rounded-[14px] border border-border-soft px-4 py-4">
            {[
              { platform: 'Mattermost', command: `/woojuin connect ${mutation.data.code}` },
              { platform: 'Discord', command: `/woojuin connect code:${mutation.data.code}` },
            ].map(({ platform, command }) => (
              <div key={platform} className="mt-4 first:mt-0">
                <div className="text-[11px] text-text-3">{platform}에서 입력하세요.</div>
                <code className="mt-2 block break-all text-sm font-bold text-text-1">
                  {command}
                </code>
                <button
                  type="button"
                  onClick={() => copyCommand(command)}
                  className="mt-2 text-xs font-semibold text-accent hover:underline"
                >
                  {platform} 명령 복사
                </button>
              </div>
            ))}
          </div>
        )}

        {discordInstallUrl && (
          <a
            href={discordInstallUrl}
            target="_blank"
            rel="noreferrer"
            className="mt-4 block w-full rounded-lg border border-border-soft px-4 py-3 text-center text-sm font-bold text-text-1 transition-colors hover:bg-surface-2"
          >
            Discord에 우주인 설치
          </a>
        )}

        <p className="mt-4 text-xs leading-5 text-text-3">
          연동 후 <code>/woojuin help</code>를 입력해보세요.
        </p>
      </Modal>
    </>
  );
};

export default ChatIntegrationCard;
