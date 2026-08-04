import { useEffect, useState } from 'react';
import axios from 'axios';
import Modal from '@/components/ui/Modal';
import SubmitButton from '@/components/ui/button/SubmitButton';
import TextInput from '@/components/ui/TextInput';
import { useRenameWorkspace } from '@/hooks/useWorkspaces';

interface RenameWorkspaceModalProps {
  workspaceId: number;
  /** 현재 이름 — 입력창의 시작값 */
  currentName: string;
  open: boolean;
  onClose: () => void;
}

/**
 * 워크스페이스 이름 변경 — 사이드바 ⋮ 메뉴에서 연다.
 *
 * ShareModal 에도 이름 변경이 있지만(연필 아이콘 인라인 편집) 그 자리는 팀 워크스페이스
 * 헤더의 공유 설정 안이라, 목록에서 바로 고치려면 스테이지로 들어갔다 나와야 했다.
 * 같은 뮤테이션 훅(useRenameWorkspace)을 쓰므로 어느 쪽으로 고쳐도 캐시 갱신은 같다.
 *
 * 사이드바는 좁아서 인라인 편집 대신 모달을 쓴다 — 접힌 상태·긴 이름·에러 메시지를
 * 좁은 폭에 밀어 넣으면 어느 것도 제대로 안 보인다(CreateWorkspaceModal 과 같은 결).
 */
const RenameWorkspaceModal = ({
  workspaceId,
  currentName,
  open,
  onClose,
}: RenameWorkspaceModalProps) => {
  const [name, setName] = useState(currentName);
  const rename = useRenameWorkspace(workspaceId);

  // 열 때마다 현재 이름으로 되돌린다 — 고치다 닫은 값이 남아 있으면 다음에 열었을 때
  // 서버의 이름과 다른 걸 보게 된다
  useEffect(() => {
    if (open) setName(currentName);
  }, [open, currentName]);

  const errorMessage = axios.isAxiosError(rename.error)
    ? (rename.error.response?.data?.message ?? '이름을 바꾸지 못했습니다')
    : null;

  const trimmed = name.trim();
  // 빈 이름이나 그대로인 이름으로 요청을 보내지 않는다
  const canSubmit = trimmed.length > 0 && trimmed !== currentName;

  return (
    <Modal open={open} onClose={onClose} title="워크스페이스 이름 변경">
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          if (!canSubmit || rename.isPending) return;
          rename.mutate(trimmed, { onSuccess: onClose });
        }}
      >
        <TextInput
          type="text"
          placeholder="워크스페이스 이름"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-label="워크스페이스 이름"
          autoComplete="off"
          autoFocus
          required
        />
        {errorMessage && <p className="text-sm text-red-500">{errorMessage}</p>}
        <SubmitButton disabled={!canSubmit} pending={rename.isPending} pendingLabel="변경 중...">
          이름 바꾸기
        </SubmitButton>
      </form>
    </Modal>
  );
};

export default RenameWorkspaceModal;
