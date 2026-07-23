import GlassButton from '@/components/ui/GlassButton';
import HeaderPopover from '@/components/ui/HeaderPopover';
import { PlusIcon } from '@/assets/icons';

/**
 * 헤더의 "새로 만들기" 버튼 — 누르면 아래로 팝오버가 뜬다.
 *
 * 팝오버 안 내용물(무엇을 만들지 고르는 메뉴/폼)은 아직 디자인 전이라
 * 자리만 잡아둔다. HeaderPopover 가 여닫기·바깥클릭·ESC 를 책임진다.
 */
const AddBtn = () => (
  <HeaderPopover trigger={<GlassButton icon={<PlusIcon />} />}>
    {/* TODO: 새로 만들기 메뉴/폼 — width·padding 은 여기서 정한다 */}
    <div className="w-[240px] p-3.5 text-sm text-text-3">준비 중</div>
  </HeaderPopover>
);

export default AddBtn;
