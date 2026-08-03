/**
 * 아이폰 기본 사진 포맷인 HEIC/HEIF 를 업로드 가능한 JPEG 로 바꾼다.
 *
 * 왜 클라이언트에서 바꾸나 — 서버는 HEIC 를 못 읽는다. 썸네일(scrimage)·OCR 전처리 모두
 * ImageIO 기반이라 디코딩이 안 되고, 목록이 원본 presigned 로 폴백해도 크롬·파이어폭스는
 * HEIC 를 렌더하지 못해 깨진 이미지가 뜬다. 자바 쪽에 쓸 만한 순수 자바 HEIC 디코더가
 * 없어 네이티브 의존(libheif)을 붙여야 하는데, 브라우저에서 wasm 으로 끝내는 편이 싸다.
 * 미리보기가 바로 되는 건 덤이다.
 *
 * <b>GPS 는 살려서 넘긴다.</b> 디코딩 결과물엔 EXIF 가 없어서 그냥 두면 좌표가 사라지는데,
 * 카메라 롤에서 바로 올린 아이폰 사진이야말로 GPS 가 붙어 있을 확률이 가장 높다 —
 * 여기서 날리면 지도 핀(FR-023)이 사실상 죽는다. 원본에서 좌표만 읽어 결과 JPEG 의
 * EXIF 로 다시 심으면 백엔드 ExifGpsReader 가 평소대로 읽는다.
 *
 * 세 라이브러리 모두 <b>동적 import</b> 다. heic-to 는 libheif wasm 을 품고 있어 3MB 에
 * 달하는데, HEIC 를 고른 사용자만 내려받으면 되는 비용이다.
 */

/** 서버 multipart 한도(10MB, application.yml)보다 낮게 잡은 변환 결과 목표 크기. */
const MAX_UPLOAD_BYTES = 9 * 1024 * 1024;

/**
 * 첫 품질로 인코딩하고, 목표 크기를 넘으면 다음 값으로 다시 인코딩한다.
 * 원본 HEIC 는 한도 안이었는데 변환 때문에 넘어가는 상황(고화소 사진)을 막기 위한 것이라
 * 실제로 2회차 이상 도는 일은 드물다. 첫 값 0.9 는 백엔드가 OCR 전처리에 쓰는 품질과 같다.
 */
const JPEG_QUALITY_STEPS = [0.9, 0.7, 0.5];

const HEIC_MIME_TYPES = new Set([
  'image/heic',
  'image/heif',
  'image/heic-sequence',
  'image/heif-sequence',
]);

const HEIC_EXTENSION = /\.hei[cf]$/i;

/**
 * 변환을 시도할 파일인지 값싸게 판별한다 — 3MB 짜리 디코더를 받아올지 말지의 기준이라
 * 여기서는 파일을 읽지 않는다. 실제 HEIC 인지는 {@link heicToJpeg} 가 컨테이너를 보고 다시 판단한다.
 */
export function isHeicCandidate(file: File): boolean {
  if (HEIC_MIME_TYPES.has(file.type.toLowerCase())) {
    return true;
  }
  // HEIC 를 모르는 브라우저는 type 을 빈 문자열로 준다 — 확장자로 한 번 더 본다.
  return HEIC_EXTENSION.test(file.name);
}

/**
 * HEIC/HEIF 파일을 JPEG File 로 바꿔 돌려준다. 원본이 실제로는 HEIC 가 아니면
 * (확장자만 그렇게 붙은 경우) 손대지 않고 그대로 돌려준다.
 *
 * @throws 디코딩 실패 시 — 호출부가 사용자에게 안내해야 한다. 깨진 파일이거나 이 브라우저가
 *         감당 못 하는 변형이라는 뜻이라, 그대로 올리면 서버에서 조용히 반쪽짜리 아이템이 된다.
 */
export async function heicToJpeg(file: File): Promise<File> {
  const { heicTo, isHeic } = await import('heic-to');
  if (!(await isHeic(file))) {
    return file;
  }

  let jpeg = await heicTo({ blob: file, type: 'image/jpeg', quality: JPEG_QUALITY_STEPS[0] });
  for (const quality of JPEG_QUALITY_STEPS.slice(1)) {
    if (jpeg.size <= MAX_UPLOAD_BYTES) break;
    jpeg = await heicTo({ blob: file, type: 'image/jpeg', quality });
  }

  const bytes = new Uint8Array(await jpeg.arrayBuffer());
  const withGps = await embedGps(bytes, await readGps(file));
  return new File([withGps], toJpegName(file.name), { type: 'image/jpeg' });
}

/** 원본 HEIC 의 EXIF 에서 좌표만 읽는다. 좌표가 없는 게 정상이므로 실패는 null 로 흡수한다. */
async function readGps(file: File): Promise<{ latitude: number; longitude: number } | null> {
  try {
    const exifr = (await import('exifr')).default;
    const gps = await exifr.gps(file);
    if (!gps || !Number.isFinite(gps.latitude) || !Number.isFinite(gps.longitude)) {
      return null;
    }
    return gps;
  } catch {
    return null;
  }
}

/**
 * JPEG 바이트에 GPS 만 담은 EXIF 를 심는다. 좌표는 부가 정보라 어떤 실패도 원본 바이트
 * 반환으로 흡수한다 — 사진 자체를 못 올리게 만들 이유가 없다(백엔드 tryApplyExifLocation 과 같은 판단).
 *
 * 부호(남위·서경)는 Ref 태그가 담당하고 도분초 값은 절댓값이다 — degToDmsRational 이
 * 내부에서 abs 를 취하므로 여기서 따로 부호를 벗기지 않는다.
 */
async function embedGps(
  jpeg: Uint8Array<ArrayBuffer>,
  gps: { latitude: number; longitude: number } | null,
): Promise<Uint8Array<ArrayBuffer>> {
  if (!gps) {
    return jpeg;
  }
  try {
    const piexif = (await import('piexifjs')).default;
    const exif = piexif.dump({
      GPS: {
        [piexif.GPSIFD.GPSVersionID]: [2, 3, 0, 0],
        [piexif.GPSIFD.GPSLatitudeRef]: gps.latitude >= 0 ? 'N' : 'S',
        [piexif.GPSIFD.GPSLatitude]: piexif.GPSHelper.degToDmsRational(gps.latitude),
        [piexif.GPSIFD.GPSLongitudeRef]: gps.longitude >= 0 ? 'E' : 'W',
        [piexif.GPSIFD.GPSLongitude]: piexif.GPSHelper.degToDmsRational(gps.longitude),
      },
    });
    return fromBinaryString(piexif.insert(exif, toBinaryString(jpeg)));
  } catch {
    return jpeg;
  }
}

/** piexifjs 는 "한 글자 = 한 바이트" 문자열로만 JPEG 를 다룬다. */
function toBinaryString(bytes: Uint8Array): string {
  // 한 번에 펼치면 인자 개수 한도에 걸려 RangeError 가 난다(수 MB 사진에서 바로 터짐).
  const CHUNK = 0x8000;
  let binary = '';
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK));
  }
  return binary;
}

function fromBinaryString(binary: string): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i) & 0xff;
  }
  return bytes;
}

/** photo.HEIC → photo.jpg. 확장자가 없으면 붙이기만 한다(파일명은 아이템 제목의 기본값이 된다). */
function toJpegName(name: string): string {
  return HEIC_EXTENSION.test(name) ? name.replace(HEIC_EXTENSION, '.jpg') : `${name}.jpg`;
}
