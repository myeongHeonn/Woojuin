import { useState } from 'react';
import Dropdown from '@/components/ui/Dropdown';
import IconButton from '@/components/ui/button/IconButton';
import TextInput from '@/components/ui/TextInput';
import SubmitButton from '@/components/ui/button/SubmitButton';
import Dot from '@/components/ui/Dot';
import { ManageIcon, PencilIcon, TrashIcon, CheckIcon, PlusIcon } from '@/assets/icons';
import { useCategories } from '@/hooks/useCategories';
import { useCategoryMutations } from '@/hooks/useCategoryMutations';

interface CategoryManageProps {
  workspaceId: number;
}

// "기타"는 분류 실패 폴백이라 항상 있어야 한다 — 서버가 수정·삭제를 막으므로
// (CategoryDefaults.ETC) 프론트도 이 행에선 수정·삭제 버튼을 아예 숨긴다.
const ETC_CATEGORY = '기타';

/**
 * 카테고리 관리 — 칩 바의 [관리] 버튼 아래로 뜨는 팝오버.
 *
 * 가운데 모달이 아니라 트리거에 붙는 드롭다운이라(Dropdown), 버튼과 패널이 한 relative
 * 안에 함께 있어야 한다. 그래서 [관리] 버튼(트리거)이 이 컴포넌트 안으로 들어온다.
 *
 * 패널은 카테고리 목록(스크롤) + 하단 추가 폼. 서버 상태는 useCategories 가,
 * 변경은 useCategoryMutations 가 맡고, 여기선 편집/삭제확인 같은 화면 상태만 든다.
 */
const CategoryManage = ({ workspaceId }: CategoryManageProps) => {
  const { data: chips = [] } = useCategories(workspaceId);
  const { create, rename, remove } = useCategoryMutations(workspaceId);

  const [newName, setNewName] = useState('');
  const [editing, setEditing] = useState<{ id: number; name: string } | null>(null);
  const [confirmId, setConfirmId] = useState<number | null>(null);

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    const name = newName.trim();
    if (!name) return;
    create.mutate(name, { onSuccess: () => setNewName('') });
  };

  const handleRename = () => {
    if (!editing) return;
    const name = editing.name.trim();
    if (!name) return;
    rename.mutate({ categoryId: editing.id, name }, { onSuccess: () => setEditing(null) });
  };

  const handleDelete = (categoryId: number) => {
    remove.mutate(categoryId, { onSuccess: () => setConfirmId(null) });
  };

  return (
    <Dropdown
      align="left"
      scrollable={false}
      renderTrigger={(toggle, open) => (
        <button
          type="button"
          onClick={toggle}
          aria-expanded={open}
          className="inline-flex h-8 shrink-0 cursor-pointer items-center gap-[7px] rounded-pill border border-border px-[13px] text-[13px] text-text-2 transition-colors hover:text-text-1 [&>svg]:h-4 [&>svg]:w-4"
        >
          <ManageIcon />
          관리
        </button>
      )}
    >
      {() => (
        <div className="flex max-h-[320px] w-72 flex-col">
          {chips.length === 0 ? (
            <p className="px-2 py-6 text-center text-sm text-text-3">카테고리가 없어요</p>
          ) : (
            // 목록만 스크롤 — 아래 추가 폼은 flex 로 하단에 고정된다(min-h-0 없으면 안 줄어듦)
            <div className="min-h-0 flex-1 space-y-0.5 overflow-y-auto scrollbar-none">
              {chips.map((chip) => {
                // "기타"는 잠금 — 수정·삭제 진입 자체를 막는다
                const locked = chip.name === ETC_CATEGORY;
                const isEditing = !locked && editing?.id === chip.categoryId;
                const isConfirming = !locked && confirmId === chip.categoryId;

                if (isEditing) {
                  return (
                    <div
                      key={chip.categoryId}
                      className="flex items-center gap-2 rounded-md bg-surface-2 px-1.5 py-1"
                    >
                      <Dot hex={chip.color} />
                      <TextInput
                        autoFocus
                        value={editing.name}
                        onChange={(e) => setEditing({ id: chip.categoryId, name: e.target.value })}
                        className="min-w-0 flex-1"
                      />
                      <IconButton label="저장" onClick={handleRename}>
                        <CheckIcon className="h-4 w-4" />
                      </IconButton>
                      <IconButton label="취소" onClick={() => setEditing(null)}>
                        ✕
                      </IconButton>
                    </div>
                  );
                }

                if (isConfirming) {
                  return (
                    <div
                      key={chip.categoryId}
                      className="flex items-center gap-2 rounded-md px-2 py-1.5"
                    >
                      <Dot hex={chip.color} />
                      <span className="flex-1 truncate text-sm text-text-1">{chip.name}</span>
                      <button
                        type="button"
                        onClick={() => handleDelete(chip.categoryId)}
                        className="h-[26px] rounded-md bg-danger px-2.5 text-xs font-medium text-surface"
                      >
                        삭제
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmId(null)}
                        className="h-[26px] rounded-md border border-border px-2.5 text-xs text-text-2"
                      >
                        취소
                      </button>
                    </div>
                  );
                }

                return (
                  <div
                    key={chip.categoryId}
                    className="flex items-center gap-2 rounded-md px-2 py-1.5"
                  >
                    <Dot hex={chip.color} />
                    <span className="flex-1 truncate text-sm text-text-1">{chip.name}</span>
                    {/* "기타"는 수정·삭제 불가 — 버튼 자체를 두지 않는다 */}
                    {!locked && (
                      <>
                        <IconButton
                          label={`${chip.name} 이름 변경`}
                          onClick={() => setEditing({ id: chip.categoryId, name: chip.name })}
                        >
                          <PencilIcon className="h-4 w-4" />
                        </IconButton>
                        <IconButton
                          label={`${chip.name} 삭제`}
                          onClick={() => setConfirmId(chip.categoryId)}
                        >
                          <TrashIcon className="h-4 w-4" />
                        </IconButton>
                      </>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          <form
            onSubmit={handleAdd}
            className="mt-1 flex shrink-0 items-center gap-2 border-t border-border px-1 pt-2"
          >
            <TextInput
              placeholder="새 카테고리 이름"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="min-w-0 flex-1"
            />
            <SubmitButton
              pending={create.isPending}
              disabled={!newName.trim()}
              className="flex shrink-0 items-center gap-1 [&>svg]:h-[15px] [&>svg]:w-[15px]"
            >
              <PlusIcon />
            </SubmitButton>
          </form>
        </div>
      )}
    </Dropdown>
  );
};

export default CategoryManage;
