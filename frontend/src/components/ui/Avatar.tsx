import { classNames } from '@/utils/classNames';

/** 사용자 아이콘 고정 3색 — userId 로 순환해 사람마다 일정한 색을 준다 */
const AVATAR_COLORS = ['#7c6cf0', '#e0864b', '#3fa96a'];

export function avatarColor(userId: number) {
  return AVATAR_COLORS[userId % AVATAR_COLORS.length];
}

interface AvatarProps {
  userId: number;
  /** 이니셜을 뽑을 이름(닉네임) */
  name: string;
  /** 지름(px) */
  size?: number;
  className?: string;
}

/** 동그란 유저 아이콘 — 고정 3색 배경 + 닉네임 첫 글자. 이미지가 없어 이니셜로 그린다. */
const Avatar = ({ userId, name, size = 28, className }: AvatarProps) => (
  <span
    aria-hidden
    className={classNames(
      'inline-grid select-none place-items-center rounded-full font-semibold text-white',
      className,
    )}
    style={{
      width: size,
      height: size,
      background: avatarColor(userId),
      fontSize: Math.round(size * 0.42),
    }}
  >
    {name.trim().charAt(0).toUpperCase() || '?'}
  </span>
);

export default Avatar;
