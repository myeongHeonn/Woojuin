import { ArrowLeftIcon } from '@/assets/icons';
import { useGoBack } from '@/hooks/useGoBack';
import { classNames } from '@/utils/classNames';

interface BackButtonProps {
  /** 눌렀을 때 갈 경로. 히스토리를 되짚지 않고 여기로 이동한다 (useGoBack 참고) */
  to?: string;
  className?: string;
}

/**
 * 뒤로 가기 — 화면 구석에 놓는 40×40 아이콘 버튼.
 *
 * 목적지를 직접 지정하는 이유는 useGoBack 에 적어 뒀다 — 앱이 히스토리를 늘리지 않아
 * 되짚을 뒤가 없다. (에러 화면처럼 라벨 붙은 다른 모양의 자리도 같은 동작을 쓴다)
 */
const BackButton = ({ to = '/', className }: BackButtonProps) => {
  const goBack = useGoBack(to);

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
