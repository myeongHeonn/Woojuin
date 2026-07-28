import type { ItemDetail } from '@/types/item';
import Dropdown from '@/components/ui/Dropdown';
import MenuItem from '@/components/ui/MenuItem';
import IconButton from '@/components/ui/button/IconButton';
import { DotsHorizontalIcon, StarIcon, TrashIcon } from '@/assets/icons';
import { useSetFavorite, useDeleteItem } from '@/hooks/useItemActions';

interface ItemActionsMenuProps {
  item: ItemDetail;
  /** 삭제 성공 후(모달 닫기 등) — 동작 결과 처리는 부모가 정한다 */
  onDeleted: () => void;
}

/**
 * 상세 모달 ⋮ 메뉴 — 즐겨찾기 토글 + 삭제. UI(Dropdown·MenuItem)와 동작(뮤테이션 훅)을 잇기만 한다.
 * 통신·캐시 갱신은 useUpdateItem/useDeleteItem 이 맡아 표현과 분리돼 있다.
 */
const ItemActionsMenu = ({ item, onDeleted }: ItemActionsMenuProps) => {
  const favorite = useSetFavorite(item.itemId);
  const del = useDeleteItem();

  return (
    <Dropdown
      renderTrigger={(toggle) => (
        <IconButton label="더보기" onClick={toggle}>
          <DotsHorizontalIcon />
        </IconButton>
      )}
    >
      {(close) => (
        <>
          <MenuItem
            icon={
              // 즐겨찾기면 별 속을 채우고 노랗게(fill-current 가 SVG fill="none" 을 덮는다)
              <StarIcon className={item.favorite ? 'fill-current text-star-yellow' : undefined} />
            }
            onClick={() => {
              favorite.mutate(!item.favorite);
              close();
            }}
          >
            {item.favorite ? '즐겨찾기 해제' : '즐겨찾기'}
          </MenuItem>
          <MenuItem
            danger
            icon={<TrashIcon />}
            onClick={() => {
              del.mutate(item.itemId, { onSuccess: onDeleted });
              close();
            }}
          >
            삭제
          </MenuItem>
        </>
      )}
    </Dropdown>
  );
};

export default ItemActionsMenu;
