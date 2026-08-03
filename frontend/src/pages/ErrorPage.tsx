import { useEffect } from 'react';
import { Link, isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { RestoreIcon } from '@/assets/icons';
import ErrorScreen from '@/components/domain/error/ErrorScreen';
import {
  errorPrimaryActionClass,
  errorSecondaryActionClass,
} from '@/components/domain/error/errorActionStyles';

/**
 * 라우트 트리에서 터진 에러를 받는 화면 — router 의 최상단 `errorElement`.
 *
 * 이게 없으면 렌더 중 예외가 앱 전체를 빈 화면으로 만든다(React 는 경계가 없으면 트리를
 * 통째로 버린다). 사용자가 할 수 있는 게 아무것도 없어지는 유일한 경우라 여기서 막는다.
 *
 * ⚠️ 잡히는 범위는 **렌더·loader·action** 이다. 이벤트 핸들러나 비동기 콜백에서 던진 예외는
 * React 의 에러 경계가 원래 잡지 못하므로, 그런 자리는 각자 try/catch + 토스트로 다뤄야 한다.
 */

/**
 * 배포로 청크가 갈린 경우를 알아본다.
 *
 * 오래 열어둔 탭은 옛 index.html 이 가리키는 옛 해시 청크를 계속 요청한다. main.tsx 가
 * vite:preloadError 를 받아 새로고침을 한 번 시도하지만, 그래도 실패하면(서비스워커가 옛
 * index.html 을 캐시에서 주는 경우 등) 여기까지 온다. 이건 고장이 아니라 "새 버전이 나왔다"는
 * 뜻이라 안내 문구가 달라야 한다.
 *
 * 메시지로 판별하는 이유: 브라우저마다 이 실패를 그냥 TypeError 로 던져서 타입으로는 못 가른다.
 */
const isChunkLoadError = (error: unknown): boolean => {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return /dynamically imported module|Importing a module script failed|Unable to preload/i.test(
    message,
  );
};

const ErrorPage = () => {
  const error = useRouteError();

  // 커스텀 errorElement 를 두면 React Router 는 에러를 콘솔에 찍지 않는다.
  // 여기서 한 번 찍어 둬야 개발·운영 모두에서 원인을 볼 수 있다.
  useEffect(() => {
    console.error('[ErrorPage] 처리되지 않은 오류', error);
  }, [error]);

  const reload = () => window.location.reload();

  // 개발 중에만 원인을 화면에 띄운다 — 운영에서는 사용자에게 의미가 없고 내부 경로가 드러난다
  const detail = import.meta.env.DEV
    ? error instanceof Error
      ? `${error.name}: ${error.message}`
      : isRouteErrorResponse(error)
        ? `${error.status} ${error.statusText}`
        : String(error)
    : undefined;

  if (isChunkLoadError(error)) {
    return (
      <ErrorScreen
        title="새 버전이 배포됐어요"
        description="화면을 새로 받아야 이어서 쓸 수 있어요. 저장한 내용은 그대로 있어요."
        detail={detail}
        actions={
          <>
            <button type="button" onClick={reload} className={errorPrimaryActionClass}>
              <RestoreIcon />
              새로고침
            </button>
            <Link to="/" className={errorSecondaryActionClass}>
              홈으로
            </Link>
          </>
        }
      />
    );
  }

  // loader·action 이 Response 를 던진 경우엔 상태 코드가 있다
  const status = isRouteErrorResponse(error) ? String(error.status) : undefined;

  return (
    <ErrorScreen
      code={status}
      title={status === '404' ? '궤도를 벗어났어요' : '잠시 교신이 끊겼어요'}
      description={
        status === '404'
          ? '이 좌표엔 아무것도 없어요. 주소가 바뀌었거나 지워진 화면일 수 있어요.'
          : '화면을 그리는 중에 문제가 생겼어요. 다시 시도해 보시고, 계속 이러면 잠시 뒤에 들어와 주세요.'
      }
      detail={detail}
      actions={
        <>
          <button type="button" onClick={reload} className={errorPrimaryActionClass}>
            <RestoreIcon />
            다시 시도
          </button>
          <Link to="/" className={errorSecondaryActionClass}>
            홈으로
          </Link>
        </>
      }
    />
  );
};

export default ErrorPage;
