import GlassButton from '@/components/ui/button/GlassButton';
import HeaderPopover from '@/components/ui/HeaderPopover';
import { UserPlusIcon } from '@/assets/icons';
import ShareModal from './ShareModal';

/**
 * 헤더 공유 버튼 — 누르면 아래로 공유 모달(링크 복사 + 멤버)이 뜬다.
 * 여닫기·바깥클릭·ESC 는 HeaderPopover, 내용은 ShareModal 이 맡는다.
 */
const ShareButton = ({ workspaceId }: { workspaceId: number }) => (
  <HeaderPopover trigger={<GlassButton icon={<UserPlusIcon />} aria-label="공유하기" />}>
    {(close) => <ShareModal workspaceId={workspaceId} onClose={close} />}
  </HeaderPopover>
);

export default ShareButton;
