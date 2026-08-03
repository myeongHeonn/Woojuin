import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import Modal from '@/components/ui/Modal';
import { issueChatLinkCode } from '@/services/integrations';

interface ChatIntegrationCardProps {
  onError: () => void;
}

const ChatIntegrationCard = ({ onError }: ChatIntegrationCardProps) => {
  const [open, setOpen] = useState(false);
  const mutation = useMutation({
    mutationFn: issueChatLinkCode,
    onError,
  });

  const copyCommand = async () => {
    if (!mutation.data) return;
    await navigator.clipboard.writeText(`/woojuin connect ${mutation.data.code}`);
  };

  return (
    <>
      <section className="rounded-[20px] border border-border-soft bg-surface p-1.5">
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="flex w-full items-center rounded-md px-5 py-[13px] text-left hover:bg-surface-2"
        >
          <span className="flex-1 text-sm font-semibold text-text-1">채팅 앱 연동</span>
          <span aria-hidden="true" className="text-lg leading-none text-text-3">
            ›
          </span>
        </button>
      </section>

      <Modal open={open} onClose={() => setOpen(false)} title="Mattermost · Discord 연동">
        <p className="text-[13px] leading-relaxed text-text-2">
          최초 연결할 때만 일회용 코드가 필요합니다. 연결 후에는 다시 발급하지 않고 바로 저장 명령을
          사용할 수 있습니다.
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
            <div className="text-[11px] text-text-3">Mattermost에서 아래 명령을 입력하세요.</div>
            <code className="mt-2 block break-all text-sm font-bold text-text-1">
              /woojuin connect {mutation.data.code}
            </code>
            <button
              type="button"
              onClick={copyCommand}
              className="mt-3 text-xs font-semibold text-accent hover:underline"
            >
              명령 복사
            </button>
          </div>
        )}
      </Modal>
    </>
  );
};

export default ChatIntegrationCard;
