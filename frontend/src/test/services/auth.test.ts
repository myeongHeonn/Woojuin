import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/services/client';
import { updateMyProfile, type UpdateProfilePayload } from '@/services/auth';

// 공용 mock 사용 — src/services/__mocks__/client.ts (파일별 팩토리는 동시 실행 시 경쟁한다)
vi.mock('@/services/client');

const patch = vi.mocked(api.patch);

describe('프로필 수정 API', () => {
  beforeEach(() => {
    patch.mockReset();
    patch.mockResolvedValue({
      data: {
        data: {
          id: 1,
          email: 'astronaut@woojuin.com',
          nickname: '새 우주인',
          profileImageUrl: null,
          provider: 'LOCAL',
          emailVerified: true,
          personalSpaceId: 1,
          avatarColor: 'PURPLE',
        },
      },
    });
  });

  it('닉네임과 프로필 정보를 PATCH /users/me로 전송한다', async () => {
    const payload: UpdateProfilePayload = {
      nickname: '새 우주인',
      profileImageUrl: null,
      avatarColor: 'PURPLE',
    };

    await updateMyProfile(payload);

    expect(patch).toHaveBeenCalledWith('/users/me', payload);
  });

  it('공통 응답의 사용자 데이터를 반환한다', async () => {
    const result = await updateMyProfile({
      nickname: '새 우주인',
      profileImageUrl: null,
      avatarColor: 'PURPLE',
    });

    expect(result.nickname).toBe('새 우주인');
    expect(result.avatarColor).toBe('PURPLE');
  });
});
