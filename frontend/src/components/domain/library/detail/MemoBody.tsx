import type { ItemDetail } from '@/types/item';
import CategoryEditor from './CategoryEditor';
import SavedMeta from './SavedMeta';

/**
 * 메모(MEMO) 상세 — 목업 m-memo. 지금은 읽기 전용(편집·저장은 이후).
 * 본문 전문(content)을 그대로 보여준다(목록엔 없음). 하단에 저장 시각.
 */
const MemoBody = ({ item, workspaceId }: { item: ItemDetail; workspaceId: number }) => (
  <div className="w-[min(560px,90vw)] p-7">
    <h2 className="mb-3 text-[22px] font-extrabold text-text-1">{item.title ?? '제목 없음'}</h2>

    <div className="mb-4">
      <CategoryEditor item={item} workspaceId={workspaceId} />
    </div>

    <p className="min-h-[160px] whitespace-pre-wrap text-[15px] leading-loose text-text-2">
      {item.content ?? ''}
    </p>

    <SavedMeta createdAt={item.createdAt} />
  </div>
);

export default MemoBody;
