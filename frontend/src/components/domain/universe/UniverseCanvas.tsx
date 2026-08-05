import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  createUniverseScene,
  type CameraState,
  type LabelPosition,
  type ScreenPosition,
  type StarNode,
  type UniverseScene,
} from '@/utils/scene';
import type { UniverseResponse } from '@/types/universe';
import ConstellationLabels, {
  type ConstellationLabel,
} from '@/components/domain/universe/ConstellationLabels';
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

  /**
   * 라벨은 "목록"과 "좌표"를 분리한다. 목록(이름·개수)만 React state 로 두고,
   * 초당 60회 바뀌는 좌표는 아래 registerNode 로 잡아 둔 DOM 노드에 직접 쓴다.
   * 좌표까지 state 로 올리면 매 프레임 리렌더가 돈다(그게 원래 구조였다).
   */
  const [labelList, setLabelList] = useState<ConstellationLabel[]>([]);
  const labelNodesRef = useRef(new Map<number, HTMLElement>());
  /** 목록이 실제로 바뀌었는지 비교할 키 — 매 프레임 setState 를 막는 유일한 장치다 */
  const labelKeyRef = useRef('');

  const [hover, setHover] = useState<HoverState | null>(null);
  const [selected, setSelected] = useState<HoverState | null>(null);

  const registerLabelNode = useCallback((categoryId: number, node: HTMLElement | null) => {
    if (node) labelNodesRef.current.set(categoryId, node);
    else labelNodesRef.current.delete(categoryId);
  }, []);

  /** 마지막으로 받은 좌표 — 목록이 새로 렌더된 직후 곧바로 반영하는 데 쓴다 */
  const latestPositionsRef = useRef<LabelPosition[]>([]);

  const applyPositions = useCallback((positions: LabelPosition[]) => {
    positions.forEach((p) => {
      const node = labelNodesRef.current.get(p.categoryId);
      if (!node) return;
      // translateY(-50%) 로 세로 중앙을 맞춘다(예전 -translate-y-1/2 클래스와 같은 역할)
      node.style.transform = `translate3d(${p.x}px, ${p.y}px, 0) translateY(-50%)`;
      node.style.opacity = p.visible ? '1' : '0';
    });
  }, []);

  /** 씬이 매 프레임 부른다 — 목록이 바뀐 경우에만 React 를 돌리고, 좌표는 DOM 에 직접 쓴다 */
  const handleLabels = useCallback(
    (positions: LabelPosition[]) => {
      latestPositionsRef.current = positions;

      const key = positions.map((p) => `${p.categoryId}:${p.name}`).join('|');
      if (key !== labelKeyRef.current) {
        labelKeyRef.current = key;
        setLabelList(positions.map(({ categoryId, name }) => ({ categoryId, name })));
      }
      applyPositions(positions);
    },
    [applyPositions],
  );

  /**
   * 목록이 새로 렌더된 직후 최신 좌표를 한 번 더 쓴다. 목록이 바뀐 프레임에는 아직 DOM
   * 노드가 없어 위 applyPositions 가 건너뛰는데, 이게 없으면 다음 프레임까지 라벨이
   * 초기값(-9999px)에 머문다. 페인트 전에 도는 layout effect 라 화면에는 안 보인다.
   */
  useLayoutEffect(() => {
    applyPositions(latestPositionsRef.current);
  }, [labelList, applyPositions]);

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
        onLabels: handleLabels,
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
      // 데이터가 바뀌면 별자리 구성도 달라진다 — 비교 키를 비워 새 목록을 반드시 다시 그린다
      labelKeyRef.current = '';
    };
  }, [data, handleLabels]);

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
        labels={labelList}
        registerNode={registerLabelNode}
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
