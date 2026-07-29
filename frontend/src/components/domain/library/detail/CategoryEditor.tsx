import type { ItemDetail } from '@/types/item';
import Dot from '@/components/ui/Dot';
import Dropdown from '@/components/ui/Dropdown';
import { useCategories } from '@/hooks/useCategories';
import { useUpdateItem } from '@/hooks/useItemActions';
import { PlusIcon } from '@/assets/icons';

interface CategoryEditorProps {
  item: ItemDetail;
  /** 추가 후보(워크스페이스 전체 카테고리)를 가져오려면 필요 */
  workspaceId: number;
}

/**
 * 아이템 카테고리 인라인 편집 — 칩의 ✕ 로 빼고, "+ 카테고리" 로 더한다.
 * 추가/삭제 모두 "새 카테고리 집합 전체"를 PATCH 로 보낸다(useUpdateItem).
 * UI 만 담고, 통신/캐시는 훅이 맡는다(동작/표현 분리).
 */
const CategoryEditor = ({ item, workspaceId }: CategoryEditorProps) => {
  const update = useUpdateItem(item.itemId);
  const { data: all = [] } = useCategories(workspaceId);

  const currentIds = item.categories.map((c) => c.categoryId);
  const commit = (ids: number[]) => update.mutate({ categoryIds: ids });
  const addable = all.filter((c) => !currentIds.includes(c.categoryId));
  // 서버가 빈 카테고리를 400 으로 막는다(최소 1개) — 마지막 1개는 ✕ 를 숨겨 애초에 못 지우게 한다
  const canRemove = item.categories.length > 1;

  return (
    <div className="flex flex-wrap items-center gap-2">
      {item.categories.map((c) => (
        <span
          key={c.categoryId}
          className="inline-flex items-center gap-1.5 rounded-full bg-surface-3 px-3 py-1 text-xs font-semibold text-text-2"
        >
          <Dot hex={c.color} />
          {c.name}
          {canRemove && (
            <button
              type="button"
              aria-label={`${c.name} 제거`}
              onClick={() => commit(currentIds.filter((x) => x !== c.categoryId))}
              className="ml-0.5 text-[10px] text-text-3 hover:text-text-1"
            >
              ✕
            </button>
          )}
        </span>
      ))}

      <Dropdown
        align="left"
        // 메모는 편집기가 모달 위쪽 → 아래로, 사진·링크는 아래쪽 → 위로 떠야 안 잘린다
        direction={item.type === 'MEMO' ? 'down' : 'up'}
        renderTrigger={(toggle) => (
          <button
            type="button"
            onClick={toggle}
            className="inline-flex items-center gap-1 rounded-full border border-dashed border-border px-3 py-1 text-xs text-text-3 hover:text-text-1 [&>svg]:h-3 [&>svg]:w-3"
          >
            <PlusIcon />
            카테고리
          </button>
        )}
      >
        {(close) =>
          addable.length === 0 ? (
            <p className="px-2.5 py-2 text-xs text-text-3">추가할 카테고리가 없어요</p>
          ) : (
            addable.map((c) => (
              <button
                key={c.categoryId}
                type="button"
                role="menuitem"
                onClick={() => {
                  commit([...currentIds, c.categoryId]);
                  close();
                }}
                className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-[13px] text-text-1 hover:bg-surface-3"
              >
                <Dot hex={c.color} />
                {c.name}
              </button>
            ))
          )
        }
      </Dropdown>
    </div>
  );
};

export default CategoryEditor;
