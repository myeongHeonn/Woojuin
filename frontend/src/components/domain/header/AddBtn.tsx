import GlassButton from '@/components/ui/button/GlassButton';
import HeaderPopover from '@/components/ui/HeaderPopover';
import { PlusIcon } from '@/assets/icons';
import AddModal from '@/components/domain/header/AddModal';

interface AddBtnProps {
  /** 감싸는 상자에 붙는 배치용 클래스 (예: 성좌 하단 바에서 desktop:hidden) */
  className?: string;
  /**
   * 온보딩 튜토리얼이 가리키는 "저장" 앵커로 쓸지 (기본 true — 헤더의 버튼).
   * 앵커가 둘이면 튜토리얼이 숨겨진 쪽을 잡을 수 있어, 재사용처(모바일 하단 바)는 false 로 끈다.
   */
  tutorial?: boolean;
}

/**
 * "새로 만들기" 버튼 — 누르면 팝오버로 AddModal(링크·사진·메모)이 뜬다.
 *
 * HeaderPopover 가 여닫기·바깥클릭·ESC 를, AddModal 이 내용물을 맡는다.
 * 헤더(상단)와 모바일 성좌 하단 바가 공유한다 — 앵커·배치만 사용처가 정한다.
 */
const AddBtn = ({ className, tutorial = true }: AddBtnProps) => (
  <div data-tutorial={tutorial ? 'save' : undefined} className={className}>
    <HeaderPopover
      trigger={
        <GlassButton
          icon={<PlusIcon />}
          aria-label="새로 만들기"
          // 테두리·+ 아이콘만 accent 로 강조(배경은 유리 그대로). GlassButton 의 기본
          // border-border·text-text-2·hover:text-text-1 과 특정성이 같아 그냥 덮으면
          // Tailwind 컴파일 순서에 밀린다 — ! 로 우선순위를 강제한다.
          className="border-accent! text-accent! hover:text-accent-hover!"
        />
      }
    >
      {(close) => <AddModal onDone={close} />}
    </HeaderPopover>
  </div>
);

export default AddBtn;
