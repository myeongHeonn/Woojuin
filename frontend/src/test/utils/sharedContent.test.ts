import { describe, it, expect } from 'vitest';
import { parseSharedContent } from '@/utils/sharedContent';

/**
 * 공유 진입은 실기기에서만 재현되므로, 앱별로 어떻게 들어오는지를 여기서 고정한다.
 * 형태는 manifest 의 share_target.params(title·text·url)가 받는 세 값의 조합이다.
 */
describe('parseSharedContent', () => {
  it('url 파라미터로 오면 그대로 쓴다 — 카톡·크롬·유튜브', () => {
    expect(parseSharedContent({ url: 'https://example.com/a' })).toEqual({
      kind: 'url',
      url: 'https://example.com/a',
    });
  });

  it('text 에 URL 이 섞여 오면 뽑아낸다 — 인스타·트위터', () => {
    expect(parseSharedContent({ text: '이거 봐 https://example.com/b 좋더라' })).toEqual({
      kind: 'url',
      url: 'https://example.com/b',
    });
  });

  it('상품명·가격이 붙어 와도 URL 만 저장한다 — 쿠팡·네이버쇼핑', () => {
    // 텍스트를 메모로 함께 남기지 않는 이유는 서버가 그 페이지를 크롤해 제목·요약을
    // 직접 뽑기 때문이다 — 남기면 같은 내용이 두 벌 된다
    const shared = parseSharedContent({
      text: '[쿠팡] 무선 이어폰 89,000원 https://coupang.com/vp/products/123',
    });

    expect(shared).toEqual({ kind: 'url', url: 'https://coupang.com/vp/products/123' });
  });

  it('url 파라미터가 있으면 text 는 보지 않는다', () => {
    // 둘 다 오는 앱이 있다. url 이 더 정확하므로 그걸 믿는다
    expect(
      parseSharedContent({ url: 'https://example.com/real', text: 'https://example.com/other' }),
    ).toEqual({ kind: 'url', url: 'https://example.com/real' });
  });

  it('URL 이 없으면 메모로 저장한다', () => {
    expect(parseSharedContent({ text: '내일 장 볼 것 정리' })).toEqual({
      kind: 'memo',
      content: '내일 장 볼 것 정리',
    });
  });

  it('제목만 오는 앱도 메모로 받는다', () => {
    // title 만 채워 보내는 앱이 있어, 무시하면 공유가 통째로 사라진다
    expect(parseSharedContent({ title: '회의 메모' })).toEqual({
      kind: 'memo',
      content: '회의 메모',
    });
  });

  it('제목과 본문이 둘 다 오면 합쳐서 메모로 만든다', () => {
    expect(parseSharedContent({ title: '제목', text: '본문' })).toEqual({
      kind: 'memo',
      content: '제목\n본문',
    });
  });

  it('아무것도 없으면 empty 다 — 저장할 게 없다', () => {
    expect(parseSharedContent({})).toEqual({ kind: 'empty' });
    expect(parseSharedContent({ url: '  ', text: '  ', title: '  ' })).toEqual({ kind: 'empty' });
  });
});
