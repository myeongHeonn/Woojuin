import { useCallback, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { classNames } from '@/utils/classNames';
import { STAGE_PX } from '@/constants/stage';
import { useTrash, useRestoreItem, useDeletePermanently, useEmptyTrash } from '@/hooks/useTrash';
import { useIntersectionObserver } from '@/hooks/useIntersectionObserver';
import TrashCard from '@/components/domain/trash/TrashCard';
import ConfirmModal from '@/components/ui/ConfirmModal';
import Spinner from '@/components/ui/Spinner';
import { ChevronLeftIcon, TrashIcon } from '@/assets/icons';

/** 확인 모달 대상 — 단건 영구삭제 또는 전체 비우기 */
type Confirm = { kind: 'delete'; itemId: number } | { kind: 'empty' } | null;

/**
 * 휴지통 — 워크스페이스별 삭제 항목 복구·영구 삭제.
 * /workspace/:workspaceId/trash 로 들어오며, 스테이지 헤더의 휴지통 아이콘이 진입점이다.
 */
const TrashPage = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  return <TrashView workspaceId={Number(workspaceId)} />;
};

const TrashView = ({ workspaceId }: { workspaceId: number }) => {
  const navigate = useNavigate();
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useTrash(workspaceId);
  const restore = useRestoreItem(workspaceId);
  const del = useDeletePermanently(workspaceId);
  const empty = useEmptyTrash(workspaceId);

  // 영구삭제·전체비우기는 되돌릴 수 없어 확인을 받는다
  const [confirm, setConfirm] = useState<Confirm>(null);

  const items = data?.pages.flatMap((page) => page.content) ?? [];

  const onIntersect = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const ref = useIntersectionObserver(onIntersect);

  return (
    <div className="flex h-full w-full flex-col overflow-hidden bg-space">
      {/* ‹ 는 왼쪽, 제목·부제는 한 컬럼으로 묶어 같은 선에 맞춘다(부제가 ‹ 가 아니라 제목과 정렬) */}
      <div
        className={classNames(
          'flex items-start justify-between gap-2 pt-[calc(40px+var(--safe-top))]',
          STAGE_PX,
        )}
      >
        <div className="flex items-start gap-1.5">
          <button
            type="button"
            aria-label="돌아가기"
            onClick={() => navigate(`/workspace/${workspaceId}/universe`)}
            className="-ml-1 grid h-8 w-8 shrink-0 place-items-center rounded-lg text-text-2 transition-colors hover:text-text-1 [&>svg]:h-5 [&>svg]:w-5"
          >
            <ChevronLeftIcon />
          </button>
          <div>
            <h1 className="text-lg font-bold text-text-1">
              휴지통 {items.length > 0 && <span className="text-text-3">{items.length}</span>}
            </h1>
            <p className="mt-2 text-xs text-text-3">
              휴지통의 항목은 30일이 지나면 자동으로 삭제돼요. 복구하면 원래 카테고리로 돌아가요.
            </p>
          </div>
        </div>

        {items.length > 0 && (
          <button
            type="button"
            onClick={() => setConfirm({ kind: 'empty' })}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs text-text-2 transition-colors hover:text-danger [&>svg]:h-4 [&>svg]:w-4"
          >
            <TrashIcon />
            전체 비우기
          </button>
        )}
      </div>

      <div
        className={classNames(
          'scrollbar-none min-h-0 flex-1 overflow-y-auto pt-6 pb-above-tabbar desktop:pb-24',
          STAGE_PX,
        )}
      >
        {isLoading ? (
          <div className="grid place-items-center py-24">
            <Spinner className="h-6 w-6" />
          </div>
        ) : items.length === 0 ? (
          <p className="py-24 text-center text-sm text-text-3">휴지통이 비어 있어요</p>
        ) : (
          <div
            className="grid justify-center gap-x-[22px] gap-y-[30px]"
            style={{ gridTemplateColumns: 'repeat(auto-fill, 124px)' }}
          >
            {items.map((item) => (
              <TrashCard
                key={item.itemId}
                item={item}
                onRestore={() => restore.mutate(item.itemId)}
                onDelete={() => setConfirm({ kind: 'delete', itemId: item.itemId })}
              />
            ))}
          </div>
        )}

        <div ref={ref} className="grid place-items-center py-6">
          {isFetchingNextPage && <Spinner className="h-6 w-6" />}
        </div>
      </div>

      <ConfirmModal
        open={confirm !== null}
        title={confirm?.kind === 'empty' ? '휴지통 비우기' : '영구 삭제'}
        description={
          confirm?.kind === 'empty'
            ? '휴지통의 모든 항목을 완전히 삭제해요. 되돌릴 수 없어요.'
            : '이 항목을 완전히 삭제해요. 되돌릴 수 없어요.'
        }
        confirmLabel={confirm?.kind === 'empty' ? '비우기' : '영구 삭제'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (confirm?.kind === 'delete') del.mutate(confirm.itemId);
          else if (confirm?.kind === 'empty') empty.mutate(items.map((item) => item.itemId));
          setConfirm(null);
        }}
      />
    </div>
  );
};

export default TrashPage;
