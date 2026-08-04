/**
 * 공유(Web Share Target)로 들어온 파일을 서비스워커 → 화면으로 넘기는 약속.
 *
 * 서비스워커(src/sw.ts)와 화면(pages/ShareTargetPage)이 **양쪽에서 같은 값을 써야** 하므로
 * 여기 한 곳에 둔다. sw.ts 를 화면에서 import 하면 워커 전용 코드가 앱 번들에 딸려온다.
 *
 * 왜 캐시로 넘기는가: POST 본문의 파일을 주소로 넘길 방법이 없다. 리다이렉트로 넘길 수 있는
 * 건 문자열뿐이라, 파일은 서비스워커가 캐시에 넣어 두고 화면이 꺼내 쓴다.
 */

/** 공유된 파일을 담아 두는 임시 캐시. 화면이 읽고 저장한 뒤 비운다 */
export const SHARE_FILE_CACHE = 'woojuin-shared-files';

/** 캐시 키 접두사 — 여러 장 공유를 `.../0`, `.../1` 순서로 담는다 */
export const SHARE_FILE_KEY_PREFIX = '/__shared-file/';

/**
 * 원본 파일명을 넘기는 헤더.
 * 이름을 잃으면 서버에 확장자 없는 blob 으로 올라가므로 인코딩해서 실어 보낸다.
 */
export const SHARE_FILE_NAME_HEADER = 'x-woojuin-shared-filename';

/** 파일이 캐시에 있다는 신호로 리다이렉트에 붙는 쿼리 값 (`?shared=files`) */
export const SHARE_FILES_FLAG = 'files';
