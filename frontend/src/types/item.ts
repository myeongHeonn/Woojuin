/** 저장 항목 처리 상태 (FR-025) */
export type ItemStatus = 'PROCESSING' | 'DONE' | 'PARTIAL' | 'FAILED';

/** 저장 항목 유형 */
export type ItemType = 'URL' | 'IMAGE' | 'MEMO';

export interface Item {
  id: number;
  type: ItemType;
  status: ItemStatus;
  title: string | null;
  summary: string | null;
  tags: string[];
  createdAt: string;
}
