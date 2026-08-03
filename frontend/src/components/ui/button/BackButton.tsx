import { ArrowLeftIcon } from '@/assets/icons';
import { useGoBack } from '@/hooks/useGoBack';
import { classNames } from '@/utils/classNames';

interface BackButtonProps {
  /** 되돌아갈 곳이 없을 때 갈 경로 */
  fallback?: string;
  className?: string;
}

/**
 * 뒤로 가기 — 화면 구석에 놓는 40×40 아이콘 버튼.
 *
 * 히스토리가 없을 때 어디로 갈지는 useGoBack 이 정한다(에러 화면처럼 라벨 붙은 다른
 * 모양의 자리도 같은 판단을 쓰도록 동작만 빼 뒀다).
 */
const BackButton = ({ fallback = '/', className }: BackButtonProps) => {
  const goBack = useGoBack(fallback);

  return (
    <button
      type="button"
      aria-label="뒤로 가기"
      onClick={goBack}
      className={classNames(
        'grid h-10 w-10 cursor-pointer place-items-center rounded-md text-text-3',
        'transition-colors hover:bg-surface-2 hover:text-text-1',
        '[&>svg]:h-5 [&>svg]:w-5',
        className,
      )}
    >
      <ArrowLeftIcon />
    </button>
  );
};

export default BackButton;
