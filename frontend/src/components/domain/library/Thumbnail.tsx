import { useState } from 'react';
import { classNames } from '@/utils/classNames';

/** src 없을 때 까는 그라디언트 폴백 — seed 로 골라 카드마다 색이 다르게 */
const GRADIENTS = [
  'linear-gradient(135deg,#26314a,#151d33)',
  'linear-gradient(135deg,#3a2f4a,#1e1830)',
  'linear-gradient(135deg,#1f3a35,#122019)',
  'linear-gradient(135deg,#4a3226,#241812)',
  'linear-gradient(135deg,#443d1e,#221e10)',
];

interface ThumbnailProps {
  /** 이미 해석된 이미지 URL. 없으면 그라디언트 폴백 */
  src: string | null;
  /** 폴백 색을 고르는 씨앗 (보통 itemId) */
  seed: number;
  className?: string;
}

/**
 * 썸네일 — 이미 정해진 src 만 받는 순수 UI. Item·s3Key 를 모른다.
 * 그 해석은 utils/itemThumbnail 이 하고, 여기서는 "있으면 이미지, 없으면 그라디언트".
 *
 * <b>background-image 가 아니라 img 를 쓴다.</b> background-image 는 loading="lazy" 가
 * 안 먹어서 화면 밖 카드의 이미지까지 전부 받는다. URL 아이템의 미리보기는 외부 원본
 * og:image 라 한 장이 1MB 를 넘기도 하는데(2026-08-04 실측: 대시보드 모바일에서 이미지
 * 13건 3,044KB — 전체 전송량의 74%, LCP 20.5초), 정작 카드는 88px 정사각이다.
 * img 로 바꾸면 뷰포트에 들어온 것만 받는다.
 */
const Thumbnail = ({ src, seed, className }: ThumbnailProps) => {
  // 깨진 URL 이면 그라디언트로 되돌린다 — img 는 background-image 와 달리 실패 시
  // 깨진 이미지 아이콘이 그대로 보인다. 플래그가 아니라 "실패한 URL" 을 기억해 두는 건,
  // src 가 다른 이미지로 바뀌었을 때 이전 실패가 따라붙지 않게 하려는 것이다.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  if (!src || src === failedSrc) {
    return <div className={className} style={{ background: GRADIENTS[seed % GRADIENTS.length] }} />;
  }

  // 바깥 상자가 크기를 정하고(className), img 는 그 안을 채우기만 한다.
  // img 를 직접 className 으로 배치하면 안 된다 — 대체 요소(replaced element)라
  // `absolute inset-0` 만으로는 늘어나지 않고 고유 크기로 그려져 카드 아래가 빈다.
  return (
    <div className={classNames('overflow-hidden', className)}>
      {/* 네이버(blogthumb.pstatic.net) 등은 자기 도메인 밖 Referer 가 붙으면 403 으로
          핫링크를 차단한다(2026-08-06 실측: Referer 없음 200, 외부 Referer 403). 상세는
          외부 og:image 원본을 그대로 쓰므로 Referer 를 아예 보내지 않아야 뜬다. */}
      <img
        src={src}
        alt=""
        loading="lazy"
        decoding="async"
        referrerPolicy="no-referrer"
        onError={() => setFailedSrc(src)}
        className="h-full w-full object-cover object-center"
      />
    </div>
  );
};

export default Thumbnail;
