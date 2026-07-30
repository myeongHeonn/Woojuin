import type { ItemType } from '@/types/item';

export const MAP_ITEM_TYPE_LABEL: Record<ItemType, string> = {
  URL: '링크',
  IMAGE: '사진',
  MEMO: '메모',
};

export const MAP_ITEM_TYPES: ItemType[] = ['MEMO', 'IMAGE', 'URL'];

export const MAP_ITEM_TYPE_COLOR: Record<ItemType, string> = {
  URL: '#8fb4ff',
  IMAGE: '#f5b08a',
  MEMO: '#b8e6a3',
};
