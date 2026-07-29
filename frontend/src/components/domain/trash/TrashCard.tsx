import type { Item } from '@/types/item';
import { classNames } from '@/utils/classNames';
import { daysUntilPurge } from '@/utils/trash';
import Dropdown from '@/components/ui/Dropdown';
import MenuItem from '@/components/ui/MenuItem';
import ItemCard from '@/components/domain/library/ItemCard';
import { RestoreIcon, TrashIcon } from '@/assets/icons';

interface TrashCardProps {
  item: Item;
  onRestore: () => void;
  /** 영구삭제 "요청" — 실제 삭제 전 확인은 부모(TrashPage)가 한다 */
  onDelete: () => void;
}

/**
 * 휴지통 카드 — 목록 카드를 그대로 쓰되, 클릭하면 상세 대신 복구/영구삭제 메뉴가 뜬다.
 * (휴지통 항목은 편집이 아니라 되돌리기·완전삭제만 의미 있으므로 ItemModal 을 열지 않는다)
 */
const TrashCard = ({ item, onRestore, onDelete }: TrashCardProps) => {
  // 삭제 후 30일 뒤 자동 영구삭제까지 남은 일수 (D-day)
  const days = daysUntilPurge(item.deletedAt);

  return (
    <Dropdown
      renderTrigger={(toggle) => (
        <div className="relative">
          <ItemCard item={item} onClick={toggle} />
          {days !== null && (
            <span
              className={classNames(
                'pointer-events-none absolute left-6 top-4 rounded-md bg-black/65 px-1.5 py-0.5 text-[10px] font-bold',
                days <= 3 ? 'text-danger' : 'text-text-1',
              )}
            >
              D-{days}
            </span>
          )}
        </div>
      )}
    >
      {(close) => (
        <>
          <MenuItem
            icon={<RestoreIcon />}
            onClick={() => {
              onRestore();
              close();
            }}
          >
            복구
          </MenuItem>
          <MenuItem
            danger
            icon={<TrashIcon />}
            onClick={() => {
              onDelete();
              close();
            }}
          >
            영구 삭제
          </MenuItem>
        </>
      )}
    </Dropdown>
  );
};

export default TrashCard;
