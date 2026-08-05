import { describe, it, expect, afterEach } from 'vitest';
import {
  SHARE_FILE_CACHE,
  SHARE_FILE_KEY_PREFIX,
  SHARE_FILE_NAME_HEADER,
} from '@/constants/shareTarget';
import { clearSharedFiles, readSharedFiles } from '@/utils/sharedFiles';

/**
 * 서비스워커가 캐시에 넣어 둔 공유 사진을 화면이 꺼내는 부분.
 *
 * 실제 공유 POST 는 실기기에서만 재현되므로, **서비스워커가 넣는 모양 그대로** 캐시를 채워
 * 읽기 쪽만 검증한다. 여기서 약속(키 접두사·파일명 헤더)이 어긋나면 사진이 조용히 사라진다.
 */
const putFile = async (index: number, file: File) => {
  const cache = await caches.open(SHARE_FILE_CACHE);
  await cache.put(
    `${SHARE_FILE_KEY_PREFIX}${index}`,
    new Response(file, {
      headers: {
        'content-type': file.type,
        [SHARE_FILE_NAME_HEADER]: encodeURIComponent(file.name),
      },
    }),
  );
};

const imageFile = (name: string) =>
  new File([new Uint8Array([1, 2, 3])], name, { type: 'image/png' });

afterEach(async () => {
  await clearSharedFiles();
});

describe('readSharedFiles', () => {
  it('캐시에 든 사진을 파일로 되살린다 — 이름과 타입까지', async () => {
    // 이름을 잃으면 서버에 확장자 없는 blob 으로 올라간다
    await putFile(0, imageFile('사진.png'));

    const files = await readSharedFiles();

    expect(files).toHaveLength(1);
    expect(files[0].name).toBe('사진.png');
    expect(files[0].type).toBe('image/png');
    expect(files[0].size).toBe(3);
  });

  it('공유된 순서를 지킨다 — 10장 이상이어도', async () => {
    // 키가 문자열이라 그냥 정렬하면 `/10` 이 `/2` 앞에 온다
    for (let index = 0; index < 11; index += 1) await putFile(index, imageFile(`${index}.png`));

    const files = await readSharedFiles();

    expect(files.map((file) => file.name)).toEqual([
      '0.png',
      '1.png',
      '2.png',
      '3.png',
      '4.png',
      '5.png',
      '6.png',
      '7.png',
      '8.png',
      '9.png',
      '10.png',
    ]);
  });

  it('공유된 사진이 없으면 빈 배열이다', async () => {
    expect(await readSharedFiles()).toEqual([]);
  });
});

describe('clearSharedFiles', () => {
  it('읽은 사진을 비운다 — 다음 공유에 지난 사진이 섞이지 않게', async () => {
    await putFile(0, imageFile('a.png'));
    expect(await readSharedFiles()).toHaveLength(1);

    await clearSharedFiles();

    expect(await readSharedFiles()).toEqual([]);
  });
});
