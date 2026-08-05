import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import Modal from '@/components/ui/Modal';
import { approveDeviceLink } from '@/services/auth';

interface WatchLinkModalProps {
  open: boolean;
  onClose: () => void;
  /** 승인 성공 시 — 부모가 기기 목록을 다시 받아 워치가 나타나게 한다 */
  onApproved: () => void;
}

/**
 * 워치 링크 코드 승인 (S15P11C105-458).
 *
 * 워치가 화면에 띄운 6자리 코드를 여기 입력하면 서버가 그 코드에 이 계정을 붙이고,
 * 워치는 폴링으로 토큰을 받아 간다 — 워치 쪽 입력이 0회가 되는 흐름의 웹 절반이다.
 */
const WatchLinkModal = ({ open, onClose, onApproved }: WatchLinkModalProps) => {
  const [code, setCode] = useState('');

  const mutation = useMutation({
    // 함수 참조를 그대로 주면 react-query 가 두 번째 인자(컨텍스트)까지 넘긴다 — 코드만 고정
    mutationFn: (linkCode: string) => approveDeviceLink(linkCode),
    onSuccess: () => {
      setCode('');
      onApproved();
    },
  });

  const close = () => {
    setCode('');
    mutation.reset();
    onClose();
  };

  const submit = () => {
    if (code.length === 6 && !mutation.isPending) mutation.mutate(code);
  };

  return (
    <Modal open={open} onClose={close} title="워치 연결">
      <p className="text-[13px] leading-relaxed text-text-2">
        워치의 우주인 앱에 표시된 6자리 코드를 입력하세요. 승인하면 워치가 이 계정으로 로그인됩니다.
      </p>

      <input
        aria-label="워치 코드"
        value={code}
        maxLength={6}
        autoCapitalize="characters"
        autoComplete="one-time-code"
        spellCheck={false}
        disabled={mutation.isPending}
        // 코드는 대문자만 발급된다 — 서버도 관용을 갖지만 화면에서 먼저 맞춰 보여준다
        onChange={(event) => setCode(event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
        onKeyDown={(event) => {
          if (event.key === 'Enter') submit();
        }}
        className="mt-4 w-full rounded-lg bg-surface-2 px-4 py-3 text-center font-mono text-xl font-extrabold tracking-[0.4em] text-text-1 outline-none focus:ring-2 focus:ring-accent"
        placeholder="······"
      />

      {mutation.isError && (
        <p className="mt-2 text-xs text-[#C74E4B]">
          코드가 만료되었거나 올바르지 않습니다. 워치에서 새 코드를 확인해 주세요.
        </p>
      )}

      {mutation.isSuccess ? (
        <p className="mt-4 text-[13px] font-semibold text-accent">
          승인되었습니다. 워치가 곧 로그인되고 기기 목록에 나타납니다.
        </p>
      ) : (
        <button
          type="button"
          disabled={code.length !== 6 || mutation.isPending}
          onClick={submit}
          className="mt-4 w-full rounded-lg bg-accent px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-40"
        >
          {mutation.isPending ? '승인 중…' : '워치 연결'}
        </button>
      )}
    </Modal>
  );
};

export default WatchLinkModal;
