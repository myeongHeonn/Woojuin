import type { Category } from './category';

/** 저장 항목 처리 상태 (FR-025) */
export type ItemStatus = 'PROCESSING' | 'DONE' | 'PARTIAL' | 'FAILED';

/** 저장 항목 유형 */
export type ItemType = 'URL' | 'IMAGE' | 'MEMO';

/** 카드·라벨에 쓰는 한글 이름 */
export const TYPE_LABEL: Record<ItemType, string> = {
  URL: '링크',
  IMAGE: '사진',
  MEMO: '메모',
};

export interface ItemPreview {
  thumbnailUrl: string | null;
  description: string | null;
}

/**
 * 목록 카드용 아이템 — 백엔드 ItemSummaryResponse 와 같은 형태.
 * 본문(content)은 오지 않는다. 요약(summary)만 온다. 본문은 상세(ItemDetail)에서 받는다.
 */
export interface Item {
  itemId: number;
  type: ItemType;
  status: ItemStatus;
  title: string | null;
  url: string | null;
  /** AI 요약 (본문이 있을 때만). 목록엔 본문 대신 이게 온다 */
  summary: string | null;
  preview: ItemPreview;
  /** 사진(IMAGE) 카드용 썸네일 URL(200px webp) — 원본은 상세에서 받는다. 분석 전이면 null */
  imageUrl: string | null;
  categories: Category[];
  favorite: boolean;
  createdAt: string;
  deletedAt: string | null;
}

/**
 * 상세 모달용 아이템 — 백엔드 ItemDetailResponse 와 같은 형태.
 * 목록(Item)에 본문 전문(content)을 더하고, imageUrl 은 원본(presigned)이다.
 * GET /workspaces/{workspaceId}/items/{itemId} 로 받는다.
 */
export interface ItemDetail extends Item {
  /** 메모/URL 본문 전문 */
  content: string | null;
}

export interface ItemListResponse {
  content: Item[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ItemCreateResponse {
  itemId: number;
  status: ItemStatus;
  createdAt: string;
}
