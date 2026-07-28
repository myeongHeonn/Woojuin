import type { Item } from '@/types/item';

/**
 * 아이템의 썸네일 소스를 정한다 — 타입마다 어디서 이미지를 얻는지 다르다.
 *
 *   IMAGE → 서버가 준 접근 URL(imageUrl). s3Key 로 직접 조립하지 않는다
 *           (버킷이 비공개라 직접 접근은 403 — 서버가 URL 을 만들어 준다)
 *   URL   → 크롤링 미리보기(preview.thumbnailUrl)
 *   MEMO  → 없음(카드가 회색 줄 스켈레톤으로 그린다)
 *
 * 카드와 상세 모달이 같은 이미지를 써야 하므로 이 판단을 한 곳에 둔다.
 */
export function resolveThumbnail(item: Item): string | null {
  if (item.type === 'IMAGE') return item.imageUrl;
  if (item.type === 'URL') return item.preview.thumbnailUrl;
  return null;
}
