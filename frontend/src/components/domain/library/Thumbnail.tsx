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
 */
const Thumbnail = ({ src, seed, className }: ThumbnailProps) => (
  <div
    className={classNames('bg-cover bg-center', className)}
    style={
      src ? { backgroundImage: `url(${src})` } : { background: GRADIENTS[seed % GRADIENTS.length] }
    }
  />
);

export default Thumbnail;
