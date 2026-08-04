import { beforeEach, describe, expect, it } from 'vitest';
import { stubApi } from '@/test/helpers/stubApi';
import { fetchUniverse } from '@/services/universe';

const get = stubApi('get');

beforeEach(() => {
  get.mockReset();
  get.mockResolvedValue({
    data: {
      data: {
        constellations: [
          {
            categoryId: 3,
            categoryName: '여행',
            color: '#C9B8FF',
            items: [
              {
                id: 11,
                position: [1.5, -2, 3],
                title: '제주 일정',
                type: 'MEMO',
              },
            ],
          },
        ],
        unclassified: [
          {
            id: 12,
            position: [4, 5, 6],
            title: null,
            type: 'IMAGE',
          },
        ],
      },
    },
  });
});

describe('우주 뷰 조회', () => {
  it('워크스페이스 우주 API를 호출한다', async () => {
    await fetchUniverse(7);
    expect(get).toHaveBeenCalledWith('/workspaces/7/universe');
  });

  it('서버 색상과 좌표를 렌더러 형식으로 정규화한다', async () => {
    const universe = await fetchUniverse(7);

    expect(universe.constellations[0]).toEqual({
      categoryId: 3,
      categoryName: '여행',
      color: 0xc9b8ff,
      items: [
        {
          id: 11,
          position: [1.5, -2, 3],
          title: '제주 일정',
          type: 'MEMO',
        },
      ],
    });
    expect(universe.unclassified[0].title).toBe('제목 없음');
  });

  it('좌표가 3차원이 아니면 잘못된 응답으로 처리한다', async () => {
    get.mockResolvedValue({
      data: {
        data: {
          constellations: [],
          unclassified: [{ id: 1, position: [1, 2], title: '잘못된 별', type: 'URL' }],
        },
      },
    });

    await expect(fetchUniverse(7)).rejects.toThrow('우주 좌표 응답이 올바르지 않습니다.');
  });
});
