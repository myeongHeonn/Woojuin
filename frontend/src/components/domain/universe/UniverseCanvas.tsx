import { useEffect, useRef, useState } from 'react';
import {
  createUniverseScene,
  type LabelPosition,
  type ScreenPosition,
  type StarNode,
  type UniverseScene,
} from '@/utils/scene';
import type { UniverseResponse } from '@/types/universe';
import ConstellationLabels from '@/components/domain/universe/ConstellationLabels';
import StarTooltip from '@/components/ui/StarTooltip';

interface UniverseCanvasProps {
  /** GET /workspaces/{id}/universe 응답을 화면용으로 정규화한 데이터 */
  data: UniverseResponse;
  /** 검색 결과 아이템 별 ID 목록 (반짝임 하이라이트) */
  highlightItemIds?: number[];
  /** 선택/포커스된 카테고리 ID (중앙 포커스 + 소속 별/선 강조) */
  activeCategoryId?: number | null;
  /** 카테고리 정보 카드를 눌렀을 때의 동작 */
  onSelectConstellation?: (categoryId: number) => void;
  /** 아이템 정보 카드를 누르면 상세 모달을 연다 */
  onOpenItem?: (itemId: number) => void;
}

interface HoverState {
  node: StarNode;
  position: ScreenPosition;
}

/**
 * 성좌 뷰 — three.js 씬을 감싸는 얇은 래퍼.
 *
 * 씬은 자기 렌더 루프를 돌고, 여기서는 오버레이(라벨·툴팁) 위치만 상태로 받는다.
 * 별 자체는 React 가 그리지 않는다.
 */
const UniverseCanvas = ({
  data,
  highlightItemIds,
  activeCategoryId,
  onSelectConstellation,
  onOpenItem,
}: UniverseCanvasProps) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const sceneRef = useRef<UniverseScene | null>(null);

  const [labels, setLabels] = useState<LabelPosition[]>([]);
  const [hover, setHover] = useState<HoverState | null>(null);
  const [selected, setSelected] = useState<HoverState | null>(null);

  /** 콜백이 매 렌더 바뀌어도 씬을 다시 만들지 않도록 ref 로 최신값만 넘긴다 */
  const handlersRef = useRef({ onSelectConstellation, onOpenItem });
  handlersRef.current = { onSelectConstellation, onOpenItem };

  /** 정보 카드를 누르면 카테고리 동작 또는 아이템 상세로 이어진다. */
  const openTooltipTarget = (node: StarNode) => {
    if (node.isHub && node.hub) {
      const catId = node.hub.categoryId;
      handlersRef.current.onSelectConstellation?.(catId);
      sceneRef.current?.setActiveCategory(catId);
      return;
    }
    if (node.star) handlersRef.current.onOpenItem?.(node.star.id);
  };

  const openTooltipTargetRef = useRef(openTooltipTarget);
  openTooltipTargetRef.current = openTooltipTarget;
  const visibleTooltip = selected ?? hover;

  useEffect(() => {
    if (!canvasRef.current) return;

    const scene = createUniverseScene(
      canvasRef.current,
      {
        onLabels: setLabels,
        onHover: (node, position) => setHover(node && position ? { node, position } : null),
        onSelect: (node, position) => {
          if (node.isHub && node.hub) {
            handlersRef.current.onSelectConstellation?.(node.hub.categoryId);
          } else {
            setSelected({ node, position });
          }
        },
        onDeselect: () => {
          setSelected(null);
        },
      },
      data,
    );
    sceneRef.current = scene;

    return () => {
      scene.dispose();
      sceneRef.current = null;
    };
  }, [data]);

  useEffect(() => {
    sceneRef.current?.setHighlightedItems(highlightItemIds ?? []);
  }, [highlightItemIds]);

  useEffect(() => {
    sceneRef.current?.setActiveCategory(activeCategoryId ?? null);
  }, [activeCategoryId]);

  return (
    <div className="relative h-full w-full overflow-hidden bg-space">
      <canvas ref={canvasRef} className="block h-full w-full" />

      <ConstellationLabels
        labels={labels}
        onSelect={(categoryId) => {
          onSelectConstellation?.(categoryId);
        }}
      />

      {visibleTooltip && !visibleTooltip.node.isHub && (
        <StarTooltip
          node={visibleTooltip.node}
          position={visibleTooltip.position}
          onClick={() => openTooltipTargetRef.current(visibleTooltip.node)}
          onPointerOverChange={(over) => sceneRef.current?.setPointerOverTooltip(over)}
        />
      )}
    </div>
  );
};

export default UniverseCanvas;
