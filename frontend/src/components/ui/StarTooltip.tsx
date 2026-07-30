import { useLayoutEffect, useRef, useState } from 'react';
import { TYPE_LABEL } from '@/types/item';
import type { ScreenPosition, StarNode } from '@/utils/scene';
import { INFO_CARD_CLASS } from '@/components/ui/infoCardStyles';

const EDGE_GAP = 12;
const STAR_GAP = 14;

interface TooltipBounds {
  containerWidth: number;
  containerHeight: number;
  cardWidth: number;
  cardHeight: number;
}

interface StarTooltipProps {
  node: StarNode;
  position: ScreenPosition;
  onClick: () => void;
  onPointerOverChange: (over: boolean) => void;
}

/**
 * 별 호버·선택 정보 카드.
 * 지도 팝업과 같은 카드 구조·스타일을 쓰고 위치 계산만 성좌 좌표계에 맞게 처리한다.
 */
const StarTooltip = ({ node, position, onClick, onPointerOverChange }: StarTooltipProps) => {
  const tooltipRef = useRef<HTMLDivElement>(null);
  const [bounds, setBounds] = useState<TooltipBounds | null>(null);
  const { isHub, hub, star, categoryName, cssColor } = node;

  useLayoutEffect(() => {
    const tooltip = tooltipRef.current;
    const container = tooltip?.parentElement;
    if (!tooltip || !container) return;

    const measure = () => {
      const next = {
        containerWidth: container.clientWidth,
        containerHeight: container.clientHeight,
        cardWidth: tooltip.offsetWidth,
        cardHeight: tooltip.offsetHeight,
      };
      setBounds((current) =>
        current &&
        current.containerWidth === next.containerWidth &&
        current.containerHeight === next.containerHeight &&
        current.cardWidth === next.cardWidth &&
        current.cardHeight === next.cardHeight
          ? current
          : next,
      );
    };

    const resizeObserver = new ResizeObserver(measure);
    resizeObserver.observe(container);
    resizeObserver.observe(tooltip);
    measure();

    return () => resizeObserver.disconnect();
  }, []);

  const cardWidth = bounds?.cardWidth ?? 0;
  const cardHeight = bounds?.cardHeight ?? 0;
  const containerWidth = bounds?.containerWidth ?? 0;
  const containerHeight = bounds?.containerHeight ?? 0;
  const maxLeft = Math.max(EDGE_GAP, containerWidth - cardWidth - EDGE_GAP);
  const maxTop = Math.max(EDGE_GAP, containerHeight - cardHeight - EDGE_GAP);
  const left = Math.min(Math.max(position.x - cardWidth / 2, EDGE_GAP), maxLeft);
  const fitsAbove = position.y - cardHeight - STAR_GAP >= EDGE_GAP;
  const fitsBelow = position.y + STAR_GAP + cardHeight <= containerHeight - EDGE_GAP;
  const placement = fitsAbove || !fitsBelow ? 'top' : 'bottom';
  const preferredTop =
    placement === 'top' ? position.y - cardHeight - STAR_GAP : position.y + STAR_GAP;
  const top = Math.min(Math.max(preferredTop, EDGE_GAP), maxTop);

  return (
    <div
      ref={tooltipRef}
      role="button"
      tabIndex={0}
      aria-label={
        isHub && hub ? `${hub.name} 카테고리 정보` : `${star?.title ?? '저장물'} 상세 열기`
      }
      data-testid="star-info-card"
      data-placement={placement}
      onClick={onClick}
      onKeyDown={(event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        event.preventDefault();
        onClick();
      }}
      onMouseEnter={() => onPointerOverChange(true)}
      onMouseLeave={() => onPointerOverChange(false)}
      style={{
        left,
        top,
        visibility: bounds && position.visible ? 'visible' : 'hidden',
      }}
      className={`${INFO_CARD_CLASS.root} pointer-events-auto absolute z-[6]`}
    >
      <div className={INFO_CARD_CLASS.meta}>
        <span
          className={INFO_CARD_CLASS.dot}
          style={{ backgroundColor: cssColor }}
          aria-hidden="true"
        />
        <span>
          {isHub
            ? '카테고리'
            : `#${categoryName ?? '미분류'}${star ? ` · ${TYPE_LABEL[star.type]}` : ''}`}
        </span>
      </div>

      {isHub && hub ? (
        <>
          <strong className={INFO_CARD_CLASS.title}>{hub.name}</strong>
          <span className={INFO_CARD_CLASS.detail}>{hub.itemCount} memories</span>
        </>
      ) : (
        star && (
          <>
            <strong className={INFO_CARD_CLASS.title}>{star.title}</strong>
            <span className={INFO_CARD_CLASS.action}>클릭하여 상세 보기</span>
          </>
        )
      )}
    </div>
  );
};

export default StarTooltip;
