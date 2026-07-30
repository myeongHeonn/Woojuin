import { createCategory, renameCategory, deleteCategory } from '@/services/workspaces';
import { useMutation, useQueryClient } from '@tanstack/react-query';

export function useCategoryMutations(workspaceId: number) {
  const queryClient = useQueryClient();

  // 카테고리 이름·소속이 달라지면 목록뿐 아니라 우주 라벨과 별자리 묶음도 갱신한다.
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['categories', workspaceId] });
    queryClient.invalidateQueries({ queryKey: ['universe', workspaceId] });
  };

  const create = useMutation({
    mutationFn: (name: string) => createCategory(workspaceId, name),
    onSuccess: invalidate,
  });

  const rename = useMutation({
    mutationFn: ({ categoryId, name }: { categoryId: number; name: string }) =>
      renameCategory(workspaceId, categoryId, name),
    onSuccess: invalidate,
  });

  const remove = useMutation({
    mutationFn: (categoryId: number) => deleteCategory(workspaceId, categoryId),
    onSuccess: invalidate,
  });

  return { create, rename, remove };
}
