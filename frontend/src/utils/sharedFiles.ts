import {
  SHARE_FILE_CACHE,
  SHARE_FILE_KEY_PREFIX,
  SHARE_FILE_NAME_HEADER,
} from '@/constants/shareTarget';

/**
 * 서비스워커가 캐시에 넣어 둔 공유 파일을 꺼낸다 (src/sw.ts 참고).
 *
 * 공유된 순서를 지킨다 — 캐시 키가 `.../0`, `.../1` 이라 문자열 정렬로는 10 이 2 앞에 오므로
 * 숫자로 비교한다(사진 열 장 이상 공유하면 순서가 뒤집힌다).
 *
 * 파일명·타입을 헤더로 복원하는 이유: Blob 그대로 업로드하면 서버에 확장자 없는 `blob` 으로
 * 올라간다. 서버가 확장자로 이미지 종류를 가리는 경로가 있어 이름을 지켜야 한다.
 */
export const readSharedFiles = async (): Promise<File[]> => {
  if (!('caches' in globalThis)) return [];

  const cache = await caches.open(SHARE_FILE_CACHE);
  const keys = await cache.keys();

  const indexOf = (request: Request) => {
    const raw = new URL(request.url).pathname.slice(SHARE_FILE_KEY_PREFIX.length);
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : Number.MAX_SAFE_INTEGER;
  };

  const ordered = keys
    .filter((request) => new URL(request.url).pathname.startsWith(SHARE_FILE_KEY_PREFIX))
    .sort((a, b) => indexOf(a) - indexOf(b));

  const files: File[] = [];
  for (const request of ordered) {
    const response = await cache.match(request);
    if (!response) continue;
    const blob = await response.blob();
    const encodedName = response.headers.get(SHARE_FILE_NAME_HEADER);
    const name = encodedName ? decodeURIComponent(encodedName) : 'shared-image';
    files.push(new File([blob], name, { type: blob.type || 'application/octet-stream' }));
  }
  return files;
};

/**
 * 꺼낸 파일을 캐시에서 지운다.
 *
 * 저장에 **성공한 뒤에만** 부른다 — 실패했는데 지우면 다시 시도할 대상이 사라져서, 사용자가
 * 공유를 처음부터 다시 해야 한다. 남겨 두면 다음 공유 때 서비스워커가 먼저 비운다.
 */
export const clearSharedFiles = async (): Promise<void> => {
  if (!('caches' in globalThis)) return;
  await caches.delete(SHARE_FILE_CACHE);
};
