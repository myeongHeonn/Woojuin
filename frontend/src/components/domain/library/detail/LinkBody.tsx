import type { ItemDetail } from '@/types/item';
import Thumbnail from '../Thumbnail';
import Section from './Section';
import CategoryEditor from './CategoryEditor';
import SavedMeta from './SavedMeta';

/**
 * 링크(URL) 상세 — 목업은 새 탭만 열지만, 여기선 요약·본문을 모달로 보여주고
 * "원문 열기" 버튼으로 새 탭을 연다(요약을 먼저 보고 원문으로 갈 수 있게).
 * 상단 이미지는 크롤링 미리보기(preview.thumbnailUrl).
 */
const LinkBody = ({ item, workspaceId }: { item: ItemDetail; workspaceId: number }) => (
  <div className="w-[min(600px,90vw)]">
    <Thumbnail
      src={item.preview.thumbnailUrl}
      seed={item.itemId}
      className="h-[260px] w-full rounded-t-xl"
    />

    <div className="space-y-4 p-6">
      <div>
        <h2 className="text-[15px] font-bold text-text-1">{item.title ?? '제목 없음'}</h2>
        {item.url && <p className="mt-1 truncate text-xs text-text-3">{item.url}</p>}
      </div>

      {item.url && (
        <a
          href={item.url}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1.5 rounded-[10px] bg-accent px-4 py-2 text-[13px] font-bold text-white hover:bg-accent-hover"
        >
          원문 열기 ↗
        </a>
      )}

      {item.summary && <Section label="요약">{item.summary}</Section>}
      <CategoryEditor item={item} workspaceId={workspaceId} />
      <SavedMeta createdAt={item.createdAt} />
    </div>
  </div>
);

export default LinkBody;
