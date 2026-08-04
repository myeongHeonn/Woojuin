import { useEffect, useRef, useState } from 'react';
import {
  createUniverseScene,
  type CameraState,
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

  /** 재생성 직후 되돌려 줄 값들 — 씬을 만드는 effect 는 [data] 에만 반응하므로 ref 로 최신값을 든다 */
  const highlightRef = useRef(highlightItemIds);
  highlightRef.current = highlightItemIds;
  const activeCategoryRef = useRef(activeCategoryId);
  activeCategoryRef.current = activeCategoryId;
  /** 직전 씬의 카메라 시점 — 다음 씬 생성 시 이어받는다(원점으로 튕기지 않게) */
  const cameraStateRef = useRef<CameraState | null>(null);

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
      cameraStateRef.current ?? undefined,
    );
    sceneRef.current = scene;

    // 하이라이트·활성 카테고리는 별도 effect([highlightItemIds]·[activeCategoryId])가
    // 값이 바뀔 때만 씬에 알린다 — 이 effect 는 [data] 에만 반응하므로, 데이터 갱신으로
    // 씬이 통째로 다시 만들어지면 새 씬은 그 상태를 모른 채 시작한다. 여기서 한 번 더 맞춰준다.
    // (activeCategory 가 있으면 setActiveCategory 내부의 focusOn 이 카메라를 그 별자리로
    // 다시 보내므로, 위에서 이어받은 카메라 시점은 활성 카테고리가 없을 때만 유지된다)
    scene.setHighlightedItems(highlightRef.current ?? []);
    scene.setActiveCategory(activeCategoryRef.current ?? null);

    return () => {
      cameraStateRef.current = scene.getCameraState();
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
