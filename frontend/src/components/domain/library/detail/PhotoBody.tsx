import type { ItemDetail } from '@/types/item';
import Thumbnail from '../Thumbnail';
import Section from './Section';
import CategoryEditor from './CategoryEditor';
import SavedMeta from './SavedMeta';
import IconButton from '@/components/ui/IconButton';
import { DownloadIcon } from '@/assets/icons';
import { downloadImage, imageFilename } from '@/utils/downloadImage';

/**
 * 사진(IMAGE) 상세 — 목업 m-photo.
 * 목록은 200px 썸네일이지만 여기선 원본(imageUrl)을 크게 보여준다.
 * 원본이 잘리지 않게 object-contain, 아직 없으면(분석 전) 그라디언트 폴백.
 * 본문 글은 AI 요약(summary)을 보여준다. 원본이 있으면 다운로드 버튼을 둔다.
 */
const PhotoBody = ({ item, workspaceId }: { item: ItemDetail; workspaceId: number }) => {
  const { imageUrl } = item;

  const handleDownload = () => {
    if (!imageUrl) return;
    downloadImage(imageUrl, imageFilename(item.title, item.itemId, imageUrl));
  };

  return (
    <div className="w-[min(600px,90vw)]">
      {imageUrl ? (
        <img
          src={imageUrl}
          alt={item.title ?? '저장한 사진'}
          className="max-h-[70vh] w-full rounded-t-xl bg-black object-contain"
        />
      ) : (
        <Thumbnail src={null} seed={item.itemId} className="h-[320px] w-full rounded-t-xl" />
      )}

      <div className="space-y-4 p-6">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-[15px] font-bold text-text-1">{item.title ?? '제목 없음'}</h2>
          {imageUrl && (
            <IconButton label="원본 다운로드" onClick={handleDownload} className="shrink-0">
              <DownloadIcon className="h-4 w-4" />
            </IconButton>
          )}
        </div>

        {item.summary && <Section label="요약">{item.summary}</Section>}
        <CategoryEditor item={item} workspaceId={workspaceId} />
        <SavedMeta createdAt={item.createdAt} />
      </div>
    </div>
  );
};

export default PhotoBody;
