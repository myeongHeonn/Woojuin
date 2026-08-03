import { describe, it, expect, vi, beforeEach } from 'vitest';
import { heicToJpeg, isHeicCandidate } from '@/utils/heicToJpeg';

// 실물 라이브러리는 libheif wasm 3MB 를 끌고 온다 — 이 파일은 변환 파이프라인의 배선만 본다
// (실제 디코딩 품질은 라이브러리 책임이고, 브라우저에서 HEIC 픽스처를 돌릴 이유가 없다).
//
// piexifjs 만 팩토리에서 객체를 그대로 돌려준다. CJS 모듈이라 `{ default: ... }` 로 감싸면
// vitest 가 한 번 더 감싸서 소스가 보는 `.default` 가 목이 아닌 래퍼가 된다.
const heicToModule = vi.hoisted(() => ({ isHeic: vi.fn(), heicTo: vi.fn() }));
const exifrModule = vi.hoisted(() => ({ gps: vi.fn() }));
const piexifModule = vi.hoisted(() => ({
  GPSIFD: {
    GPSVersionID: 0,
    GPSLatitudeRef: 1,
    GPSLatitude: 2,
    GPSLongitudeRef: 3,
    GPSLongitude: 4,
  },
  GPSHelper: { degToDmsRational: vi.fn(() => [[1, 1]]) },
  dump: vi.fn<(exifDict: { GPS?: Record<number, unknown> }) => string>(),
  insert: vi.fn<(exif: string, jpeg: string) => string>(),
}));

vi.mock('heic-to', () => heicToModule);
vi.mock('exifr', () => ({ default: exifrModule }));
vi.mock('piexifjs', () => piexifModule);

const heic = (name = 'IMG_0001.HEIC') => new File(['heic-bytes'], name, { type: 'image/heic' });

/** heicTo 가 돌려줄 가짜 JPEG. size 로 품질 재시도 분기를 흉내낸다. */
const jpegBlob = (size: number) => new Blob(['j'.repeat(size)], { type: 'image/jpeg' });

/** dump 로 넘어간 GPS 태그 묶음 */
const dumpedGps = () => piexifModule.dump.mock.calls[0][0].GPS!;

beforeEach(() => {
  heicToModule.isHeic.mockReset().mockResolvedValue(true);
  heicToModule.heicTo.mockReset().mockResolvedValue(jpegBlob(10));
  exifrModule.gps.mockReset().mockResolvedValue(undefined);
  piexifModule.dump.mockReset().mockReturnValue('EXIF');
  piexifModule.insert.mockReset().mockImplementation((exif, jpeg) => `${jpeg}${exif}`);
});

describe('isHeicCandidate', () => {
  it('HEIC/HEIF 계열 MIME 을 잡는다', () => {
    for (const type of ['image/heic', 'image/heif', 'image/heic-sequence', 'IMAGE/HEIC']) {
      expect(isHeicCandidate(new File([''], 'p', { type }))).toBe(true);
    }
  });

  it('type 이 비어 있으면 확장자로 잡는다', () => {
    // HEIC 를 모르는 브라우저는 type 을 채워주지 않는다 — 파일 선택기에서 실제로 겪는 경우
    expect(isHeicCandidate(new File([''], 'IMG_0001.HEIC', { type: '' }))).toBe(true);
    expect(isHeicCandidate(new File([''], 'a.heif', { type: '' }))).toBe(true);
  });

  it('일반 이미지는 건드리지 않는다', () => {
    expect(isHeicCandidate(new File([''], 'a.png', { type: 'image/png' }))).toBe(false);
    expect(isHeicCandidate(new File([''], 'heic-사진.jpg', { type: 'image/jpeg' }))).toBe(false);
  });
});

describe('heicToJpeg', () => {
  it('JPEG File 로 바꾸고 확장자도 .jpg 로 맞춘다', async () => {
    const result = await heicToJpeg(heic());
    expect(result.type).toBe('image/jpeg');
    expect(result.name).toBe('IMG_0001.jpg');
  });

  it('확장자가 없으면 .jpg 를 붙인다', async () => {
    // 파일명은 아이템 제목의 기본값이라 잘라내기보다 붙이는 쪽이 안전하다
    expect((await heicToJpeg(heic('IMG_0002'))).name).toBe('IMG_0002.jpg');
  });

  it('실제 컨테이너가 HEIC 가 아니면 원본을 그대로 돌려준다', async () => {
    // 확장자만 .heic 으로 잘못 붙은 파일 — 변환했다가 오히려 멀쩡한 사진을 망친다
    heicToModule.isHeic.mockResolvedValue(false);
    const original = heic();
    expect(await heicToJpeg(original)).toBe(original);
    expect(heicToModule.heicTo).not.toHaveBeenCalled();
  });

  it('결과가 한도 안이면 품질 0.9 한 번으로 끝낸다', async () => {
    await heicToJpeg(heic());
    expect(heicToModule.heicTo).toHaveBeenCalledTimes(1);
    expect(heicToModule.heicTo.mock.calls[0][0]).toMatchObject({ quality: 0.9 });
  });

  it('한도를 넘으면 품질을 낮춰 다시 인코딩한다', async () => {
    // 원본 HEIC 는 10MB 한도 안이었는데 변환하면서 넘어가는 고화소 사진 대비
    const tooBig = () => jpegBlob(9 * 1024 * 1024 + 1);
    heicToModule.heicTo
      .mockResolvedValueOnce(tooBig())
      .mockResolvedValueOnce(tooBig())
      .mockResolvedValueOnce(jpegBlob(10));

    await heicToJpeg(heic());
    expect(heicToModule.heicTo.mock.calls.map((call) => call[0].quality)).toEqual([0.9, 0.7, 0.5]);
  });

  it('원본의 GPS 를 결과 JPEG 의 EXIF 로 옮긴다', async () => {
    // 디코딩 결과엔 EXIF 가 없어서 이 단계가 없으면 지도 핀(FR-023)이 사라진다
    exifrModule.gps.mockResolvedValue({ latitude: 37.5, longitude: 127.03 });

    await heicToJpeg(heic());

    expect(piexifModule.insert).toHaveBeenCalledTimes(1);
    expect(dumpedGps()[piexifModule.GPSIFD.GPSLatitudeRef]).toBe('N');
    expect(dumpedGps()[piexifModule.GPSIFD.GPSLongitudeRef]).toBe('E');
  });

  it('남위·서경은 Ref 로 표시한다', async () => {
    // 도분초 값은 절댓값이고 부호는 Ref 가 담당한다 — 뒤집히면 지구 반대편에 핀이 찍힌다
    exifrModule.gps.mockResolvedValue({ latitude: -33.87, longitude: -70.66 });

    await heicToJpeg(heic());

    expect(dumpedGps()[piexifModule.GPSIFD.GPSLatitudeRef]).toBe('S');
    expect(dumpedGps()[piexifModule.GPSIFD.GPSLongitudeRef]).toBe('W');
  });

  it('GPS 가 없으면 EXIF 를 심지 않는다', async () => {
    await heicToJpeg(heic());
    expect(piexifModule.insert).not.toHaveBeenCalled();
  });

  it('EXIF 처리가 실패해도 사진은 올라간다', async () => {
    // 좌표는 부가 정보다 — 이것 때문에 저장 자체를 막지 않는다
    exifrModule.gps.mockResolvedValue({ latitude: 37.5, longitude: 127.03 });
    piexifModule.insert.mockImplementation(() => {
      throw new Error('insert 실패');
    });

    const result = await heicToJpeg(heic());
    expect(result.type).toBe('image/jpeg');
    expect(result.size).toBe(10);
  });

  it('디코딩이 실패하면 예외를 그대로 올린다', async () => {
    // 호출부가 안내 문구를 띄워야 한다 — 원본을 그냥 올리면 서버에서 반쪽짜리 아이템이 된다
    heicToModule.heicTo.mockRejectedValue(new Error('디코딩 실패'));
    await expect(heicToJpeg(heic())).rejects.toThrow();
  });
});
