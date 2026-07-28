/** 카테고리 — 백엔드 CategoryResponse 와 같은 형태 (id·이름·hex 색) */
export interface Category {
  categoryId: number;
  name: string;
  /** 카드/별자리 표시용 hex 색 (예 "#C9B8FF") */
  color: string;
}
