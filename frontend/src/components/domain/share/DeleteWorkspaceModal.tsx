import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import Modal from '@/components/ui/Modal';
import TextInput from '@/components/ui/TextInput';
import { deleteWorkspace, fetchMembers, getCategories } from '@/services/workspaces';
import { fetchItems } from '@/services/items';
import { useWorkspaces } from '@/hooks/useWorkspaces';

interface DeleteWorkspaceModalProps {
  workspaceId: number;
  open: boolean;
  onClose: () => void;
}

/**
 * 워크스페이스 삭제 확인 모달 — 공유 모달의 "삭제하기"(OWNER 전용)로 연다.
 * 되돌릴 수 없는 삭제라 두 겹으로 막는다: 무엇을 잃는지(아이템·카테고리·멤버 수)를
 * 먼저 보여주고, 워크스페이스 이름을 그대로 입력해야 버튼이 열린다.
 * 진입점이 TEAM 헤더에만 있어(PERSONAL 은 공유 버튼이 없다) 개인 스페이스는 여기 못 온다.
 */
const DeleteWorkspaceModal = ({ workspaceId, open, onClose }: DeleteWorkspaceModalProps) => {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [text, setText] = useState('');

  // 이름은 삭제 확인의 기준값 — 목록 캐시(사이드바·헤더가 이미 채워둠)에서 찾는다
  const { data: workspaces } = useWorkspaces();
  const name = workspaces?.find((workspace) => workspace.id === workspaceId)?.name ?? '';

  // 영향 요약 — 전용 API 가 없어 기존 셋을 조합한다. 아이템 수는 목록 응답의
  // totalElements 만 필요하므로 size 1 로 가장 싼 페이지를 부른다.
  const { data: impact } = useQuery({
    queryKey: ['workspace-impact', workspaceId],
    enabled: open,
    queryFn: async () => {
      const [items, categories, members] = await Promise.all([
        fetchItems({ workspaceId, size: 1, page: 0 }),
        getCategories(workspaceId),
        fetchMembers(workspaceId),
      ]);
      return {
        itemCount: items.totalElements,
        categoryCount: categories.length,
        memberCount: members.length,
      };
    },
  });

  // 닫을 때 입력을 비운다 — 다시 열었을 때 확인이 채워져 있으면 안전장치가 무력해진다
  const close = () => {
    setText('');
    onClose();
  };

  const deleteMutation = useMutation({
    mutationFn: () => deleteWorkspace(workspaceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      close();
      navigate('/home');
    },
  });

  const errorMessage = axios.isAxiosError(deleteMutation.error)
    ? (deleteMutation.error.response?.data?.message ?? '워크스페이스 삭제에 실패했습니다')
    : null;

  const confirmed = text.trim() === name && name.length > 0;

  return (
    <Modal open={open} onClose={close} title="워크스페이스 삭제">
      <form
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (confirmed && !deleteMutation.isPending) deleteMutation.mutate();
        }}
      >
        {impact && (
          <div className="rounded-lg bg-surface-2 px-3.5 py-2.5 text-[13px] leading-relaxed text-text-2">
            <p>
              아이템 <span className="font-semibold text-text-1">{impact.itemCount}개</span> ·
              카테고리 <span className="font-semibold text-text-1">{impact.categoryCount}개</span>가
              삭제돼요
            </p>
            <p>
              멤버 <span className="font-semibold text-text-1">{impact.memberCount}명</span>의
              접근이 끊겨요
            </p>
          </div>
        )}

        <p className="text-[13px] leading-relaxed text-text-2">
          되돌릴 수 없어요. 확인을 위해 <span className="font-semibold text-text-1">{name}</span>{' '}
          을(를) 입력하세요.
        </p>

        <TextInput
          type="text"
          placeholder={name}
          value={text}
          onChange={(e) => setText(e.target.value)}
          aria-label="워크스페이스 이름 확인"
          autoComplete="off"
          autoFocus
        />

        {errorMessage && <p className="text-sm text-red-500">{errorMessage}</p>}

        <button
          type="submit"
          disabled={!confirmed || deleteMutation.isPending}
          className="rounded-[11px] bg-[#B3423F] px-5 py-2.5 text-[13.5px] font-bold text-white transition-colors hover:bg-[#C74E4B] disabled:cursor-not-allowed disabled:opacity-40"
        >
          {deleteMutation.isPending ? '삭제 중...' : '영구 삭제'}
        </button>
      </form>
    </Modal>
  );
};

export default DeleteWorkspaceModal;
