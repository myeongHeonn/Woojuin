export type MapCategoryId = 'seongsu' | 'jeju' | 'coffee' | 'travel';

export type MapItemType = 'LINK' | 'IMAGE' | 'MEMO';

export interface MapCategory {
  id: MapCategoryId;
  label: string;
  color: string;
}

export interface MapPlace {
  id: number;
  categoryId: MapCategoryId;
  type: MapItemType;
  title: string;
  description: string;
  position: {
    x: number;
    y: number;
  };
}
