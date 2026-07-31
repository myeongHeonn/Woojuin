import { classNames } from '@/utils/classNames';
import { getAvatarSwatch, getAvatarTextColor } from '@/utils/avatarSwatch';

interface AvatarProps {
  /** 이니셜을 뽑을 이름(닉네임) */
  name: string;
  /**
   * 그 사람이 마이페이지에서 고른 우주인 색상. 서버 값("BLUE")을 그대로 넘겨도 된다.
   * 모르는 색·빈 값이면 흰색으로 대체된다.
   */
  color?: string | null;
  /** 지름(px) */
  size?: number;
  className?: string;
}

/**
 * 동그란 유저 아이콘 — 본인이 고른 색 배경 + 닉네임 첫 글자.
 *
 * 색을 userId 로 계산하지 않는다. 계산하면 마이페이지에서 고른 색이 공유 워크스페이스에
 * 반영되지 않아 같은 사람이 화면마다 다른 색으로 보인다(사이드바는 고른 색, 멤버 목록은 계산색).
 * 배경은 사용자 데이터라 토큰으로 표현할 수 없어 style 로 들어간다.
 */
const Avatar = ({ name, color, size = 28, className }: AvatarProps) => (
  <span
    aria-hidden
    className={classNames(
      'inline-grid select-none place-items-center rounded-full font-semibold',
      className,
    )}
    style={{
      width: size,
      height: size,
      background: getAvatarSwatch(color),
      color: getAvatarTextColor(color),
      fontSize: Math.round(size * 0.42),
    }}
  >
    {name.trim().charAt(0).toUpperCase() || '?'}
  </span>
);

export default Avatar;
