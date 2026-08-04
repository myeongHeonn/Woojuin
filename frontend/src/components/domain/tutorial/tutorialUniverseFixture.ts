import type { UniverseResponse } from '@/types/universe';

const tutorialUniverseFixtureBase: UniverseResponse = {
  constellations: [
    {
      categoryId: -101,
      categoryName: '여행·장소',
      color: 0x7c6cf0,
      items: [
        { id: -1001, title: '서울 여행 코스', type: 'URL', position: [-7, 3, 0] },
        { id: -1002, title: '가보고 싶은 장소', type: 'IMAGE', position: [-5, 1, 1] },
        { id: -1003, title: '여행 준비 메모', type: 'MEMO', position: [-8, -1, -1] },
        { id: -1022, title: '제주 숨은 명소', type: 'URL', position: [-10, 2, 2] },
        { id: -1023, title: '부산 바다 사진', type: 'IMAGE', position: [-6, -2, 0] },
      ],
    },
    {
      categoryId: -102,
      categoryName: '문화·콘텐츠',
      color: 0x9ba8ff,
      items: [
        { id: -1004, title: '좋아하는 음악', type: 'URL', position: [4, 4, 0] },
        { id: -1005, title: '전시 기록', type: 'IMAGE', position: [6, 2, 1] },
        { id: -1006, title: '보고 싶은 영화', type: 'MEMO', position: [3, 1, -1] },
        { id: -1024, title: '주말 공연 일정', type: 'URL', position: [7, 5, -1] },
        { id: -1025, title: '읽고 싶은 책', type: 'MEMO', position: [2, 3, 2] },
        { id: -1026, title: '플레이리스트', type: 'URL', position: [5, 0, 0] },
      ],
    },
    {
      categoryId: -103,
      categoryName: '학습·지식',
      color: 0x8fb4ff,
      items: [
        { id: -1007, title: 'React 공부 자료', type: 'URL', position: [-1, 6, 0] },
        { id: -1008, title: '프로젝트 아이디어', type: 'MEMO', position: [1, 5, 1] },
        { id: -1009, title: '강의 필기', type: 'IMAGE', position: [-2, 4, -1] },
        { id: -1027, title: 'TypeScript 정리', type: 'URL', position: [-4, 7, 1] },
        { id: -1028, title: '알고리즘 노트', type: 'MEMO', position: [2, 7, -2] },
        { id: -1029, title: '발표 자료', type: 'IMAGE', position: [-3, 5, 2] },
      ],
    },
    {
      categoryId: -104,
      categoryName: '음식·맛집',
      color: 0xf2d96b,
      items: [
        { id: -1010, title: '성수 맛집 목록', type: 'URL', position: [-1, -3, 0] },
        { id: -1011, title: '주말 브런치', type: 'IMAGE', position: [1, -4, 1] },
        { id: -1012, title: '먹어 보고 싶은 메뉴', type: 'MEMO', position: [-2, -5, -1] },
        { id: -1030, title: '을지로 카페', type: 'URL', position: [2, -6, -1] },
        { id: -1031, title: '집밥 레시피', type: 'IMAGE', position: [-4, -4, 2] },
      ],
    },
    {
      categoryId: -105,
      categoryName: '쇼핑·제품',
      color: 0xd2caed,
      items: [
        { id: -1013, title: '사고 싶은 키보드', type: 'URL', position: [7, -2, 0] },
        { id: -1014, title: '인테리어 소품', type: 'IMAGE', position: [8, -4, 1] },
        { id: -1015, title: '장바구니 메모', type: 'MEMO', position: [5, -4, -1] },
        { id: -1032, title: '노트북 비교', type: 'URL', position: [10, -1, 2] },
        { id: -1033, title: '선물 후보', type: 'IMAGE', position: [6, -6, 0] },
      ],
    },
    {
      categoryId: -106,
      categoryName: '건강·운동',
      color: 0xa8d8b9,
      items: [
        { id: -1016, title: '홈트레이닝 루틴', type: 'URL', position: [-9, -5, 0] },
        { id: -1017, title: '러닝 기록', type: 'IMAGE', position: [-7, -6, 1] },
        { id: -1018, title: '운동 계획', type: 'MEMO', position: [-10, -3, -1] },
        { id: -1034, title: '스트레칭 영상', type: 'URL', position: [-11, -7, 1] },
        { id: -1035, title: '건강검진 기록', type: 'IMAGE', position: [-6, -8, -2] },
      ],
    },
    {
      categoryId: -107,
      categoryName: '아이디어·영감',
      color: 0xf2a7b8,
      items: [
        { id: -1019, title: '서비스 아이디어', type: 'MEMO', position: [9, 5, 0] },
        { id: -1020, title: '디자인 레퍼런스', type: 'IMAGE', position: [10, 3, 1] },
        { id: -1021, title: '영감을 준 글', type: 'URL', position: [8, 2, -1] },
        { id: -1036, title: '새 기능 스케치', type: 'IMAGE', position: [11, 6, 2] },
        { id: -1037, title: '문구 아이디어', type: 'MEMO', position: [7, 7, -2] },
      ],
    },
    {
      categoryId: -108,
      categoryName: '생활·할 일',
      color: 0x8fd3c7,
      items: [
        { id: -1038, title: '이번 주 할 일', type: 'MEMO', position: [-13, 5, 0] },
        { id: -1039, title: '이사 체크리스트', type: 'URL', position: [-11, 7, 1] },
        { id: -1040, title: '방 정리 기록', type: 'IMAGE', position: [-14, 3, -1] },
        { id: -1041, title: '장보기 목록', type: 'MEMO', position: [-10, 4, 2] },
      ],
    },
    {
      categoryId: -109,
      categoryName: '취업·커리어',
      color: 0xf3a683,
      items: [
        { id: -1042, title: '관심 기업 공고', type: 'URL', position: [13, -5, 0] },
        { id: -1043, title: '면접 질문 정리', type: 'MEMO', position: [11, -7, 1] },
        { id: -1044, title: '포트폴리오 참고', type: 'IMAGE', position: [14, -3, -1] },
        { id: -1045, title: '커리어 목표', type: 'MEMO', position: [10, -4, 2] },
        { id: -1046, title: '채용 설명회', type: 'URL', position: [13, -8, -2] },
      ],
    },
    {
      categoryId: -110,
      categoryName: '돈·재테크',
      color: 0x86c5a4,
      items: [
        { id: -1047, title: '월간 지출 기록', type: 'MEMO', position: [0, -9, 0] },
        { id: -1048, title: '적금 상품 비교', type: 'URL', position: [3, -10, 1] },
        { id: -1049, title: '예산표 캡처', type: 'IMAGE', position: [-2, -11, -1] },
        { id: -1050, title: '투자 공부 자료', type: 'URL', position: [1, -12, 2] },
      ],
    },
    {
      categoryId: -111,
      categoryName: '기타',
      color: 0xb8b8c8,
      items: [
        { id: -1051, title: '나중에 읽을 글', type: 'URL', position: [14, 7, 0] },
        { id: -1052, title: '분류할 사진', type: 'IMAGE', position: [12, 9, 1] },
        { id: -1053, title: '짧은 메모', type: 'MEMO', position: [15, 5, -1] },
      ],
    },
  ],
  unclassified: [],
};

// 빈 우주뷰에서도 각 성좌가 한곳에 뭉치지 않고 화면 전반에 펼쳐져 보이도록
// 튜토리얼 전용 좌표만 넓힌다. 실제 사용자 데이터에는 영향을 주지 않는다.
export const tutorialUniverseFixture: UniverseResponse = {
  ...tutorialUniverseFixtureBase,
  constellations: tutorialUniverseFixtureBase.constellations.map((constellation) => ({
    ...constellation,
    items: constellation.items.map((item) => ({
      ...item,
      position: [item.position[0] * 2.2, item.position[1] * 1.75, item.position[2]],
    })),
  })),
};

export const isUniverseEmpty = (universe: UniverseResponse) =>
  universe.unclassified.length === 0 &&
  universe.constellations.every((constellation) => constellation.items.length === 0);
