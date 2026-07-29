import { useState } from 'react';
import type { ItemDetail } from '@/types/item';
import CategoryEditor from './CategoryEditor';
import SavedMeta from './SavedMeta';
import { useUpdateItem } from '@/hooks/useItemActions';

/**
 * 메모(MEMO) 상세 — 목업 m-memo. 제목·본문을 그 자리에서 편집한다(PATCH title/content).
 * 바뀐 게 있을 때만 저장 버튼이 뜨고, 저장하면 목록·상세 캐시가 갱신된다.
 * (모달은 한 번에 한 메모만 열고 닫을 때 언마운트되므로, 편집 버퍼는 mount 시 item 으로 채운다)
 */
const MemoBody = ({ item, workspaceId }: { item: ItemDetail; workspaceId: number }) => {
  const update = useUpdateItem(item.itemId);
  const [title, setTitle] = useState(item.title ?? '');
  const [content, setContent] = useState(item.content ?? '');

  const dirty = title !== (item.title ?? '') || content !== (item.content ?? '');

  const save = () => {
    if (dirty) update.mutate({ title: title.trim(), content });
  };

  return (
    <div className="w-[min(560px,90vw)] p-7">
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        aria-label="제목"
        placeholder="제목 없음"
        className="mb-3 w-full bg-transparent text-[22px] font-extrabold text-text-1 outline-none placeholder:text-text-3"
      />

      <div className="mb-4">
        <CategoryEditor item={item} workspaceId={workspaceId} />
      </div>

      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        aria-label="본문"
        placeholder="메모를 입력하세요"
        className="min-h-[160px] w-full resize-none bg-transparent text-[15px] leading-loose text-text-2 outline-none placeholder:text-text-3"
      />

      <div className="mt-2 flex items-center justify-between gap-2">
        <SavedMeta createdAt={item.createdAt} />
        {dirty && (
          <button
            type="button"
            onClick={save}
            disabled={update.isPending}
            className="shrink-0 rounded-lg bg-accent px-4 py-1.5 text-[13px] font-semibold text-white transition-colors hover:bg-accent-hover disabled:opacity-60"
          >
            {update.isPending ? '저장 중…' : '저장'}
          </button>
        )}
      </div>
    </div>
  );
};

export default MemoBody;
