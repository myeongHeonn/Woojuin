import { describe, it, expect } from 'vitest';
import { imageFilename } from '@/utils/downloadImage';

describe('imageFilename', () => {
  it('제목 + URL 확장자로 파일명을 만든다', () => {
    expect(imageFilename('성수 영수증', 7, 'https://s3/x/photo.jpg?sig=abc')).toBe(
      '성수 영수증.jpg',
    );
  });

  it('제목이 없으면 "우주인-{id}" 로 대체한다', () => {
    expect(imageFilename(null, 42, 'https://s3/x/a.png?y=1')).toBe('우주인-42.png');
    expect(imageFilename('   ', 42, 'https://s3/x/a.webp')).toBe('우주인-42.webp');
  });

  it('확장자를 못 찾으면 png 로 둔다', () => {
    expect(imageFilename('무확장', 1, 'https://s3/x/blob?token=z')).toBe('무확장.png');
  });

  it('파일명에 못 쓰는 문자는 _ 로 바꾼다', () => {
    expect(imageFilename('a/b:c*d?', 1, 'https://s3/x/i.jpg')).toBe('a_b_c_d_.jpg');
  });
});
