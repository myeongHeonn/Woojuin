import type { ItemDetail } from '@/types/item';
import { useItem } from '@/hooks/useItem';
import Overlay from '@/components/ui/Overlay';
import CloseButton from '@/components/ui/button/CloseButton';
import ItemActionsMenu from './ItemActionsMenu';
import PhotoBody from './PhotoBody';
import LinkBody from './LinkBody';
import MemoBody from './MemoBody';

interface ItemModalProps {
  workspaceId: number;
  /** 열 아이템 id. null 이면 닫힘 — 렌더도, 요청도 안 한다 */
  itemId: number | null;
  onClose: () => void;
}

/** 타입에 맞는 바디를 고른다 — 셸(닫기·로딩)과 표현을 분리(SRP) */
function ItemBody({ item, workspaceId }: { item: ItemDetail; workspaceId: number }) {
  if (item.type === 'IMAGE') return <PhotoBody item={item} workspaceId={workspaceId} />;
  if (item.type === 'URL') return <LinkBody item={item} workspaceId={workspaceId} />;
  return <MemoBody item={item} workspaceId={workspaceId} />;
}

/**
 * 아이템 상세 모달 — 데이터(useItem)·로딩 판단·타입 디스패치만 맡고,
 * 오버레이·배경/Esc 닫기 같은 껍데기 동작은 Overlay 가 맡는다(동작/표현 분리).
 * 데이터는 useItem 이 itemId 있을 때만 받아온다.
 */
const ItemModal = ({ workspaceId, itemId, onClose }: ItemModalProps) => {
  const { data: item, isLoading, isError } = useItem(workspaceId, itemId);

  if (itemId == null) return null;
  // 로딩 중엔 모달을 아예 띄우지 않는다 — 데이터가 오면 그때 뜬다(스피너 깜빡임 없이)
  if (isLoading) return null;

  return (
    <Overlay
      onClose={onClose}
      cardClassName="max-h-[86vh] overflow-auto border border-border scrollbar-none"
    >
      <div className="absolute right-3.5 top-3.5 z-10 flex items-center gap-2">
        {item && <ItemActionsMenu item={item} onDeleted={onClose} />}
        <CloseButton onClick={onClose} />
      </div>

      {isError && (
        <div className="grid h-40 w-[min(560px,90vw)] place-items-center text-sm text-text-3">
          불러오지 못했어요
        </div>
      )}
      {item && <ItemBody item={item} workspaceId={workspaceId} />}
    </Overlay>
  );
};

export default ItemModal;
