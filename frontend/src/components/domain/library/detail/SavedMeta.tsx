import { formatRelativeDate } from '@/utils/formatDate';

/**
 * 상세 모달 공용 하단 — 저장 시각. 세 타입(사진·링크·메모) 모두에 나온다.
 * 값이 없거나 못 읽으면 자리 자체를 비운다.
 */
const SavedMeta = ({ createdAt }: { createdAt: string }) => {
  const saved = formatRelativeDate(createdAt);
  if (!saved) return null;

  return (
    <div className="mt-5 border-t border-border-soft pt-3.5 text-xs text-text-3">{saved} 저장</div>
  );
};

export default SavedMeta;
