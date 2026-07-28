import IconButton from './IconButton';

interface CloseButtonProps {
  onClick: () => void;
  /** 배치용 (absolute right-3.5 top-3.5 …) */
  className?: string;
}

/** 모달 닫기(✕) — 아이콘 버튼 틀(IconButton)에 ✕ 만 얹은 것. */
const CloseButton = ({ onClick, className }: CloseButtonProps) => (
  <IconButton label="닫기" onClick={onClick} className={className}>
    ✕
  </IconButton>
);

export default CloseButton;
