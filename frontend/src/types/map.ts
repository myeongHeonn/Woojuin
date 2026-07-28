import type { ItemType } from '@/types/item';

export type MapCategoryId = number;

export interface MapPlace {
  itemId: number;
  categoryIds: MapCategoryId[];
  type: ItemType;
  title: string | null;
  lat: number;
  lng: number;
  address: string;
}
