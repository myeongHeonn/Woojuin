import type { MapCategory, MapItemType, MapPlace } from '@/types/map';

export const MAP_CATEGORIES: MapCategory[] = [
  { id: 'seongsu', label: 'Seongsu', color: 'var(--color-star-orange)' },
  { id: 'jeju', label: 'Jeju', color: 'var(--color-star-green)' },
  { id: 'coffee', label: 'Coffee', color: 'var(--color-star-yellow)' },
  { id: 'travel', label: 'Travel', color: 'var(--color-star-blue)' },
];

export const MAP_ITEM_TYPE_LABEL: Record<MapItemType, string> = {
  LINK: '링크',
  IMAGE: '사진',
  MEMO: '메모',
};

/**
 * 지도 API가 정해지기 전까지 쓰는 화면용 데이터.
 * position은 실제 위경도가 아니라 현재 지도 자리 안에서의 백분율 좌표다.
 */
export const MAP_PLACES: MapPlace[] = [
  {
    id: 1,
    categoryId: 'seongsu',
    type: 'IMAGE',
    title: '성수 플레이스 팝업',
    description: '7/20–8/3 · 11:00–20:00',
    position: { x: 34, y: 28 },
  },
  {
    id: 2,
    categoryId: 'seongsu',
    type: 'LINK',
    title: '성수 편집샵',
    description: '인스타 공유로 저장',
    position: { x: 47, y: 37 },
  },
  {
    id: 3,
    categoryId: 'seongsu',
    type: 'MEMO',
    title: '우주인 쇼룸',
    description: '주말 방문 예정',
    position: { x: 62, y: 25 },
  },
  {
    id: 4,
    categoryId: 'coffee',
    type: 'MEMO',
    title: '로우키 성수',
    description: '예가체프 핸드드립',
    position: { x: 55, y: 53 },
  },
  {
    id: 5,
    categoryId: 'coffee',
    type: 'LINK',
    title: '센터커피 서울숲',
    description: '플랫화이트 추천 메모',
    position: { x: 26, y: 48 },
  },
  {
    id: 6,
    categoryId: 'coffee',
    type: 'IMAGE',
    title: '블루보틀 성수',
    description: '싱글오리진 원두 구매',
    position: { x: 69, y: 43 },
  },
  {
    id: 7,
    categoryId: 'travel',
    type: 'IMAGE',
    title: '서울숲',
    description: '피크닉 스팟 저장',
    position: { x: 20, y: 66 },
  },
  {
    id: 8,
    categoryId: 'travel',
    type: 'LINK',
    title: '뚝섬한강공원',
    description: '야경 사진 스크랩',
    position: { x: 43, y: 72 },
  },
  {
    id: 9,
    categoryId: 'jeju',
    type: 'IMAGE',
    title: '성산일출봉',
    description: '매표소 07:00 오픈',
    position: { x: 60, y: 68 },
  },
  {
    id: 10,
    categoryId: 'jeju',
    type: 'LINK',
    title: '애월 카페거리',
    description: '제주 3박4일 코스',
    position: { x: 15, y: 38 },
  },
  {
    id: 11,
    categoryId: 'jeju',
    type: 'MEMO',
    title: '새별오름',
    description: '억새 시즌 메모',
    position: { x: 72, y: 61 },
  },
];
