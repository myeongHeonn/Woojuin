import type { Theme } from '@/stores/themeAtoms';

export interface MapPoint {
  id: number;
  lat: number;
  lng: number;
  title: string;
  address: string;
  categoryLabel: string;
  typeLabel: string;
  color: string;
}

export interface MapAdapter {
  setPoints: (points: MapPoint[]) => void;
  selectPoint: (pointId: number | null) => void;
  /** 앱 테마가 바뀌면 베이스맵도 따라간다 — 밝은 화면에 어두운 지도만 남으면 구멍처럼 보인다. */
  setTheme: (theme: Theme) => void;
  resize: () => void;
  destroy: () => void;
}

export interface MapAdapterOptions {
  container: HTMLElement;
  /** 첫 렌더에 쓸 테마. 이후 변경은 setTheme 으로 받는다(지도를 다시 만들지 않는다). */
  theme: Theme;
  onSelectPoint: (pointId: number) => void;
  onOpenPoint: (pointId: number) => void;
  onDeselectPoint: () => void;
  onResetView: () => void;
}
