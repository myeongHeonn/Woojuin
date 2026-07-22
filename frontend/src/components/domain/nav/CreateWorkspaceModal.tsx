import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import Modal from '@/components/ui/Modal';
import { createWorkspace } from '@/services/workspaces';

interface CreateWorkspaceModalProps {
  open: boolean;
  onClose: () => void;
}

/** 사이드바 "New workspace" 클릭 시 뜨는 생성 폼 */
const CreateWorkspaceModal = ({ open, onClose }: CreateWorkspaceModalProps) => {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');

  const createMutation = useMutation({
    mutationFn: createWorkspace,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      setName('');
      onClose();
    },
  });

  const errorMessage = axios.isAxiosError(createMutation.error)
    ? (createMutation.error.response?.data?.message ?? '워크스페이스 생성에 실패했습니다')
    : null;

  return (
    <Modal open={open} onClose={onClose} title="새 워크스페이스">
      <form
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          createMutation.mutate({ name, type: 'TEAM' });
        }}
      >
        <input
          type="text"
          placeholder="워크스페이스 이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="rounded-md border border-border bg-surface-2 px-3 py-2 text-text-1 outline-none focus:border-accent"
          autoFocus
          required
        />
        {errorMessage && <p className="text-sm text-red-500">{errorMessage}</p>}
        <button
          type="submit"
          disabled={createMutation.isPending}
          className="rounded-md bg-accent px-3 py-2 font-semibold text-white hover:bg-accent-hover disabled:opacity-50"
        >
          {createMutation.isPending ? '생성 중...' : '만들기'}
        </button>
      </form>
    </Modal>
  );
};

export default CreateWorkspaceModal;
