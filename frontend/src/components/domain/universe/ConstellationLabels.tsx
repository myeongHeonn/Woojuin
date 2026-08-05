/** 라벨 목록 — 이름·id 만. 좌표는 매 프레임 바뀌므로 여기 들어오지 않는다. */
export interface ConstellationLabel {
  categoryId: number;
  name: string;
}

interface ConstellationLabelsProps {
  labels: ConstellationLabel[];
  onSelect: (categoryId: number) => void;
  /** 좌표를 직접 쓸 수 있도록 DOM 노드를 등록한다(언마운트 시 null) */
  registerNode: (categoryId: number, node: HTMLElement | null) => void;
}

/**
 * 별자리 이름 오버레이 — 목업 `.clabel`.
 *
 * <b>좌표를 props 로 받지 않는다.</b> 3D 투영 좌표는 초당 60회 바뀌는데 그걸 React
 * state 로 올리면 매 프레임 리렌더가 돈다 — scene.ts 주석의 "React 밖에 두는 이유"가
 * 별에는 지켜졌는데 라벨에서 새어 나가 있었다. 여기서는 목록(이름·개수)이 바뀔 때만
 * 그리고, 위치는 UniverseCanvas 가 registerNode 로 받아둔 노드에 직접 쓴다.
 *
 * left/top 이 아니라 transform 을 쓰는 이유: transform 은 레이아웃을 다시 계산하지
 * 않아 매 프레임 갱신해도 싸다.
 */
const ConstellationLabels = ({ labels, onSelect, registerNode }: ConstellationLabelsProps) => (
  <div className="pointer-events-none absolute inset-0 z-[4]">
    {labels.map((label) => (
      <button
        key={label.categoryId}
        ref={(node) => registerNode(label.categoryId, node)}
        type="button"
        onClick={() => onSelect(label.categoryId)}
        // 첫 프레임이 좌표를 쓰기 전엔 화면 밖에 숨겨 둔다(왼쪽 위에 잠깐 튀는 것 방지)
        style={{ transform: 'translate3d(-9999px,-9999px,0)', opacity: 0 }}
        className="pointer-events-auto absolute left-0 top-0 cursor-pointer whitespace-nowrap text-[12.5px] font-semibold text-[rgba(232,234,238,.92)] transition-opacity duration-200 [text-shadow:0_1px_8px_rgba(0,0,0,.95)]"
      >
        {label.name}
      </button>
    ))}
  </div>
);

export default ConstellationLabels;
