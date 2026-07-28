import { createCategory, renameCategory, deleteCategory } from '@/services/workspaces';
import { useMutation, useQueryClient } from '@tanstack/react-query';

export function useCategoryMutations(workspaceId: number) {
  const queryClient = useQueryClient();

  //성공 후 공유하고 있는 서버 상태를 바꿔줘야한다.
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['categories', workspaceId] });

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
