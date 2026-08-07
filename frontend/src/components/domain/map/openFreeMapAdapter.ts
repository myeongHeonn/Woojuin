import {
  LngLat,
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  Popup,
  setWorkerUrl,
  type IControl,
} from 'maplibre-gl';
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url';
import maximizeIconUrl from '@/assets/icons/tabler-maximize.svg?url';
import type { MapAdapter, MapAdapterOptions, MapPoint } from '@/components/domain/map/mapAdapter';
import { INFO_CARD_CLASS } from '@/components/ui/infoCardStyles';
import type { Theme } from '@/stores/themeAtoms';

/**
 * 테마별 베이스맵.
 *
 * fiord 는 배경이 #45516E 인 어두운 청회색, positron 은 rgb(242,243,240) 인 밝은 회색이다.
 * 라이트에 positron 을 고른 이유: 앱의 라이트 캔버스(--color-space #f4f5f8)와 톤이 거의 같아
 * 지도와 페이지가 이어져 보이고, 채도가 낮아 컬러 마커(--marker-color)가 묻히지 않는다.
 * bright·liberty 도 밝지만 따뜻한 베이지(#f8f4f0)에 채도가 높아 마커와 색이 부딪힌다.
 */
const OPEN_FREE_MAP_STYLES: Record<Theme, string> = {
  dark: 'https://tiles.openfreemap.org/styles/fiord',
  light: 'https://tiles.openfreemap.org/styles/positron',
};
const DEFAULT_CENTER: [number, number] = [127.7669, 35.9078];
const CLUSTER_RADIUS_PX = 36;
const CLUSTER_ZOOM_STEP = 2;
const MAX_CLUSTER_ZOOM = 18;
const MIN_CLUSTER_SIZE_PX = 28;
const MAX_CLUSTER_SIZE_PX = 52;
const CLUSTER_SIZE_STEP_PX = 4;
const LARGE_CLUSTER_COUNT = 5;
const DISTANT_PLACE_THRESHOLD_METERS = 800_000;
const SELECTED_PLACE_ZOOM = 17.5;

setWorkerUrl(maplibreWorkerUrl);

const createResetViewControl = (onReset: () => void): IControl => {
  let control: HTMLDivElement | null = null;

  return {
    onAdd() {
      control = document.createElement('div');
      control.className = 'maplibregl-ctrl maplibregl-ctrl-group woojuin-map-reset-view-control';

      const button = document.createElement('button');
      button.type = 'button';
      button.title = '전체 장소 보기';
      button.setAttribute('aria-label', '전체 장소 보기');
      button.addEventListener('click', onReset);

      const icon = document.createElement('img');
      icon.className = 'woojuin-map-reset-view-icon';
      icon.src = maximizeIconUrl;
      icon.alt = '';
      button.append(icon);
      control.append(button);
      return control;
    },
    onRemove() {
      control?.remove();
      control = null;
    },
  };
};

const createPopupContent = (point: MapPoint, onOpen: (pointId: number) => void) => {
  const content = document.createElement('article');
  content.className = INFO_CARD_CLASS.root;
  content.tabIndex = 0;
  content.setAttribute('role', 'button');
  content.setAttribute('aria-label', `${point.title} 상세 열기`);

  const meta = document.createElement('div');
  meta.className = INFO_CARD_CLASS.meta;

  const dot = document.createElement('span');
  dot.className = INFO_CARD_CLASS.dot;
  dot.style.backgroundColor = point.color;

  const type = document.createElement('span');
  type.textContent = `#${point.categoryLabel} · ${point.typeLabel}`;

  const title = document.createElement('strong');
  title.className = INFO_CARD_CLASS.title;
  title.textContent = point.title;

  const address = document.createElement('span');
  address.className = INFO_CARD_CLASS.detail;
  address.textContent = point.address;

  const openHint = document.createElement('span');
  openHint.className = INFO_CARD_CLASS.action;
  openHint.textContent = '클릭하여 열기';

  meta.append(dot, type);
  content.append(meta, title, address, openHint);
  content.addEventListener('click', (event) => {
    event.stopPropagation();
    onOpen(point.id);
  });
  content.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    event.stopPropagation();
    onOpen(point.id);
  });

  return content;
};

export const createOpenFreeMapAdapter = ({
  container,
  theme,
  onSelectPoint,
  onOpenPoint,
  onDeselectPoint,
  onResetView,
}: MapAdapterOptions): MapAdapter => {
  let currentTheme = theme;
  const map = new MapLibreMap({
    container,
    style: OPEN_FREE_MAP_STYLES[theme],
    center: DEFAULT_CENTER,
    zoom: 6.2,
    attributionControl: { compact: true },
  });

  const collapseInitialAttribution = () => {
    const attribution = container.querySelector<HTMLDetailsElement>(
      '.maplibregl-ctrl-attrib.maplibregl-compact',
    );
    if (!attribution) return;

    attribution.classList.remove('maplibregl-compact-show');
    attribution.setAttribute('open', '');
    map.off('styledata', collapseInitialAttribution);
  };

  map.on('styledata', collapseInitialAttribution);
  collapseInitialAttribution();

  let points: MapPoint[] = [];
  let markers: Marker[] = [];
  let markerElements = new Map<number, HTMLButtonElement>();
  let selectedPopup: Popup | null = null;
  let hoverPopup: Popup | null = null;
  let hoverCloseTimer: ReturnType<typeof setTimeout> | null = null;
  let selectedPointId: number | null = null;

  const removeSelectedPopup = () => {
    selectedPopup?.remove();
    selectedPopup = null;
  };

  const removeHoverPopup = () => {
    if (hoverCloseTimer) {
      clearTimeout(hoverCloseTimer);
      hoverCloseTimer = null;
    }
    hoverPopup?.remove();
    hoverPopup = null;
  };

  const scheduleHoverPopupRemoval = () => {
    if (hoverCloseTimer) clearTimeout(hoverCloseTimer);
    hoverCloseTimer = setTimeout(removeHoverPopup, 120);
  };

  const showHoverPopup = (point: MapPoint) => {
    if (selectedPointId !== null) return;

    removeHoverPopup();
    hoverPopup = new Popup({
      closeButton: false,
      closeOnClick: false,
      offset: 18,
      className: 'woojuin-map-popup',
    })
      .setLngLat([point.lng, point.lat])
      .setDOMContent(createPopupContent(point, onOpenPoint))
      .addTo(map);

    const popupElement = hoverPopup.getElement();
    popupElement.addEventListener('mouseenter', () => {
      if (hoverCloseTimer) {
        clearTimeout(hoverCloseTimer);
        hoverCloseTimer = null;
      }
    });
    popupElement.addEventListener('mouseleave', scheduleHoverPopupRemoval);
  };

  const updateMarkerSelection = () => {
    markerElements.forEach((element, id) => {
      const selected = id === selectedPointId;
      element.classList.toggle('is-selected', selected);
      element.setAttribute('aria-pressed', String(selected));
    });
  };

  const updateSelection = () => {
    updateMarkerSelection();
    removeHoverPopup();
    removeSelectedPopup();
    if (selectedPointId === null) return;

    const point = points.find(({ id }) => id === selectedPointId);
    if (!point) return;

    selectedPopup = new Popup({
      closeButton: false,
      closeOnClick: false,
      offset: 18,
      className: 'woojuin-map-popup',
    })
      .setLngLat([point.lng, point.lat])
      .setDOMContent(createPopupContent(point, onOpenPoint))
      .addTo(map);

    const destination = new LngLat(point.lng, point.lat);
    const isDistant = map.getCenter().distanceTo(destination) >= DISTANT_PLACE_THRESHOLD_METERS;

    if (isDistant) {
      map.flyTo({
        center: destination,
        zoom: SELECTED_PLACE_ZOOM,
        curve: 1.6,
        speed: 1.8,
        minZoom: 7,
      });
    } else {
      map.easeTo({
        center: destination,
        zoom: Math.max(map.getZoom(), SELECTED_PLACE_ZOOM),
        duration: 450,
      });
    }
  };

  const removeMarkers = () => {
    removeHoverPopup();
    markers.forEach((marker) => marker.remove());
    markers = [];
    markerElements = new Map();
  };

  const handleMapClick = () => {
    onDeselectPoint();
  };

  const createMarker = (clusterPoints: MapPoint[]) => {
    const isCluster = clusterPoints.length > 1;
    const lat = clusterPoints.reduce((sum, point) => sum + point.lat, 0) / clusterPoints.length;
    const lng = clusterPoints.reduce((sum, point) => sum + point.lng, 0) / clusterPoints.length;
    const markerAnchor = document.createElement('div');
    markerAnchor.className = 'woojuin-map-marker-anchor';

    const element = document.createElement('button');
    element.type = 'button';
    element.className = 'woojuin-map-marker';

    if (isCluster) {
      const clusterSize = Math.min(
        MIN_CLUSTER_SIZE_PX + (clusterPoints.length - 2) * CLUSTER_SIZE_STEP_PX,
        MAX_CLUSTER_SIZE_PX,
      );
      markerAnchor.classList.add('has-cluster');
      markerAnchor.style.setProperty('--cluster-size', `${clusterSize}px`);
      element.classList.add('is-cluster');
      if (clusterPoints.length >= LARGE_CLUSTER_COUNT) {
        element.classList.add('is-large');
      }
      element.textContent = String(clusterPoints.length);
      element.setAttribute('aria-label', `가까운 장소 ${clusterPoints.length}곳 확대`);
      element.addEventListener('click', (event) => {
        event.stopPropagation();
        const anchor = map.project([clusterPoints[0].lng, clusterPoints[0].lat]);
        const farthestDistance = clusterPoints.reduce((maximum, point) => {
          const projected = map.project([point.lng, point.lat]);
          return Math.max(maximum, Math.hypot(projected.x - anchor.x, projected.y - anchor.y));
        }, 0);
        const zoomDelta =
          farthestDistance > 0
            ? Math.max(Math.log2((CLUSTER_RADIUS_PX + 4) / farthestDistance), 0.5)
            : CLUSTER_ZOOM_STEP;

        map.easeTo({
          center: [lng, lat],
          zoom: Math.min(map.getZoom() + zoomDelta, MAX_CLUSTER_ZOOM),
          duration: 450,
        });
      });
    } else {
      const [point] = clusterPoints;
      element.style.setProperty('--marker-color', point.color);
      element.setAttribute('aria-label', `${point.title} 지도 위치`);
      element.addEventListener('click', (event) => {
        event.stopPropagation();
        onSelectPoint(point.id);
      });
      element.addEventListener('mouseenter', () => showHoverPopup(point));
      element.addEventListener('mouseleave', scheduleHoverPopupRemoval);
      element.addEventListener('focus', () => showHoverPopup(point));
      element.addEventListener('blur', scheduleHoverPopupRemoval);
      markerElements.set(point.id, element);
    }

    markerAnchor.append(element);
    markers.push(
      new Marker({ element: markerAnchor, anchor: 'center' }).setLngLat([lng, lat]).addTo(map),
    );
  };

  const renderMarkers = () => {
    removeMarkers();
    const clusters: MapPoint[][] = [];

    points.forEach((point) => {
      const projectedPoint = map.project([point.lng, point.lat]);
      const cluster = clusters.find(([anchor]) => {
        const projectedAnchor = map.project([anchor.lng, anchor.lat]);
        return (
          Math.hypot(projectedPoint.x - projectedAnchor.x, projectedPoint.y - projectedAnchor.y) <=
          CLUSTER_RADIUS_PX
        );
      });

      if (cluster) {
        cluster.push(point);
      } else {
        clusters.push([point]);
      }
    });

    clusters.forEach(createMarker);
    updateMarkerSelection();
  };

  const fitPoints = () => {
    if (points.length === 0) return;

    const width = container.clientWidth;
    const height = container.clientHeight;
    if (width < 2 || height < 2) return;

    if (points.length === 1) {
      map.easeTo({ center: [points[0].lng, points[0].lat], zoom: 15, duration: 500 });
      return;
    }

    const isDesktop = width >= 640;
    const requestedPadding = isDesktop
      ? { top: 112, right: 430, bottom: 150, left: 88 }
      : { top: 88, right: 64, bottom: Math.round(height * 0.55), left: 40 };
    const horizontalBudget = width - 1;
    const verticalBudget = height - 1;
    const left = Math.min(requestedPadding.left, Math.floor(horizontalBudget / 2));
    const top = Math.min(requestedPadding.top, Math.floor(verticalBudget / 2));
    const padding = {
      top,
      right: Math.min(requestedPadding.right, horizontalBudget - left),
      bottom: Math.min(requestedPadding.bottom, verticalBudget - top),
      left,
    };

    const bounds = new LngLatBounds();
    points.forEach((point) => bounds.extend([point.lng, point.lat]));
    map.fitBounds(bounds, {
      padding,
      maxZoom: 14,
      duration: 650,
    });
  };

  map.addControl(
    new NavigationControl({
      showCompass: false,
      showZoom: true,
    }),
    'bottom-right',
  );

  map.addControl(
    createResetViewControl(() => {
      onResetView();
      fitPoints();
    }),
    'bottom-right',
  );

  map.on('moveend', renderMarkers);
  map.on('click', handleMapClick);

  return {
    setPoints(nextPoints) {
      points = nextPoints;
      renderMarkers();
      updateSelection();
      fitPoints();
    },
    selectPoint(pointId) {
      selectedPointId = pointId;
      updateSelection();
    },
    setTheme(nextTheme) {
      if (nextTheme === currentTheme) return;
      currentTheme = nextTheme;
      // 마커·팝업은 DOM 오버레이라 스타일 교체의 영향을 받지 않는다(지도 레이어가 아니다).
      // 그래서 다시 그릴 필요 없이 베이스맵만 갈아 끼우면 된다 — 카메라 위치도 유지된다.
      map.setStyle(OPEN_FREE_MAP_STYLES[nextTheme]);
    },
    resize() {
      map.resize();
    },
    destroy() {
      removeHoverPopup();
      removeSelectedPopup();
      map.off('moveend', renderMarkers);
      map.off('click', handleMapClick);
      removeMarkers();
      map.remove();
    },
  };
};
