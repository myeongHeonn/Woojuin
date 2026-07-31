import { getSpacemanImage, type SpacemanColor } from '@/utils/getSpacemanImage';
import { getAvatarSwatch } from '@/utils/avatarSwatch';

/**
 * 선택 순서와 한글 이름만 여기서 정한다 — 실제 색값은 getAvatarSwatch 가 단일 소스다.
 * 여기에 색값을 또 적으면 아바타와 선택 화면이 서로 다른 색을 보여줄 수 있다.
 */
const COLORS: Array<{ name: SpacemanColor; label: string }> = [
  { name: 'white', label: '화이트' },
  { name: 'black', label: '블랙' },
  { name: 'red', label: '레드' },
  { name: 'crimson', label: '크림슨' },
  { name: 'pink', label: '핑크' },
  { name: 'orange', label: '오렌지' },
  { name: 'yellow', label: '라임' },
  { name: 'green', label: '그린' },
  { name: 'blue', label: '블루' },
  { name: 'navy', label: '네이비' },
  { name: 'purple', label: '퍼플' },
];

interface AvatarColorPickerProps {
  value: string;
  disabled?: boolean;
  onChange: (color: string) => void;
}

const AvatarColorPicker = ({ value, disabled, onChange }: AvatarColorPickerProps) => {
  const normalizedValue = value.toLowerCase();

  return (
    <section className="mb-4 rounded-[20px] border border-border-soft bg-surface px-[26px] py-[22px]">
      <h2 className="mb-4 text-xs font-bold tracking-[0.1em] text-text-3">우주인 색상</h2>
      <div className="flex flex-wrap items-center gap-5">
        <div className="h-[58px] w-[58px] shrink-0 overflow-hidden rounded-[15px] border border-border bg-surface-3">
          <img
            src={getSpacemanImage(normalizedValue)}
            alt="선택한 우주인 색상 미리보기"
            className="h-full w-full object-cover"
          />
        </div>
        <div role="radiogroup" aria-label="우주인 색상" className="flex flex-wrap gap-[11px]">
          {COLORS.map(({ name, label }) => {
            const selected = name === normalizedValue;
            return (
              <button
                key={name}
                type="button"
                role="radio"
                aria-checked={selected}
                aria-label={label}
                title={label}
                disabled={disabled}
                onClick={() => onChange(name.toUpperCase())}
                style={{ backgroundColor: getAvatarSwatch(name) }}
                className={[
                  'h-[30px] w-[30px] rounded-full border-2 transition-transform hover:scale-110 disabled:cursor-wait disabled:opacity-50',
                  selected
                    ? 'border-accent ring-[3px] ring-surface'
                    : 'border-border focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
                ].join(' ')}
              />
            );
          })}
        </div>
      </div>
      <p className="mt-3.5 text-xs text-text-3">
        선택한 색상은 사이드바와 프로필의 우주인 이미지에 함께 적용됩니다.
      </p>
    </section>
  );
};

export default AvatarColorPicker;
