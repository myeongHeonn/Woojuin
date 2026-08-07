import type { ItemDetail } from '@/types/item';
import IconButton from '@/components/ui/button/IconButton';
import { StarIcon, TrashIcon } from '@/assets/icons';
import { useSetFavorite, useDeleteItem } from '@/hooks/useItemActions';

interface ItemActionsProps {
  item: ItemDetail;
  /** 삭제 성공 후(모달 닫기 등) — 동작 결과 처리는 부모가 정한다 */
  onDeleted: () => void;
}

/**
 * 상세 모달 우상단 액션 — 즐겨찾기 토글 + 삭제(휴지통행, 복구 가능).
 *
 * 닫기(✕)와 같은 IconButton 틀을 써서 한 줄에 나란히 놓이고, 도달성(접근성)도 ✕와 같아진다.
 * 예전엔 ⋮ 드롭다운 뒤에 숨어 있어 두 번 눌러야 닿았다 — 자주 쓰는 동작이라 직접 노출한다.
 * UI(IconButton)와 동작(뮤테이션 훅)을 잇기만 한다(표현/동작 분리).
 */
const ItemActions = ({ item, onDeleted }: ItemActionsProps) => {
  const favorite = useSetFavorite(item.itemId);
  const del = useDeleteItem();

  return (
    <>
      <IconButton
        label={item.favorite ? '즐겨찾기 해제' : '즐겨찾기'}
        onClick={() => favorite.mutate(!item.favorite)}
      >
        {/* 즐겨찾기면 별 속을 채우고 노랗게(fill-current 가 SVG fill="none" 을 덮는다) */}
        <StarIcon className={item.favorite ? 'h-4 w-4 fill-current text-star-yellow' : 'h-4 w-4'} />
      </IconButton>
      <IconButton
        label="삭제"
        className="hover:text-danger"
        onClick={() => del.mutate(item.itemId, { onSuccess: onDeleted })}
      >
        <TrashIcon className="h-4 w-4" />
      </IconButton>
    </>
  );
};

export default ItemActions;
