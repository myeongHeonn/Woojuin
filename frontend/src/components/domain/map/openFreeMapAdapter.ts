import {
  LngLat,
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  Popup,
  setWorkerUrl,
} from 'maplibre-gl';
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url';
import type { MapAdapter, MapAdapterOptions, MapPoint } from '@/components/domain/map/mapAdapter';

const OPEN_FREE_MAP_STYLE = 'https://tiles.openfreemap.org/styles/fiord';
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

const createPopupContent = (point: MapPoint) => {
  const content = document.createElement('article');
  content.className = 'woojuin-map-popup-content';

  const meta = document.createElement('div');
  meta.className = 'woojuin-map-popup-meta';

  const dot = document.createElement('span');
  dot.className = 'woojuin-map-popup-dot';
  dot.style.backgroundColor = point.color;

  const type = document.createElement('span');
  type.textContent = `#${point.categoryLabel} · ${point.typeLabel}`;

  const title = document.createElement('strong');
  title.textContent = point.title;

  const address = document.createElement('span');
  address.className = 'woojuin-map-popup-address';
  address.textContent = point.address;

  meta.append(dot, type);
  content.append(meta, title, address);
  return content;
};

export const createOpenFreeMapAdapter = ({
  container,
  onSelectPoint,
  onDeselectPoint,
}: MapAdapterOptions): MapAdapter => {
  const map = new MapLibreMap({
    container,
    style: OPEN_FREE_MAP_STYLE,
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

  map.addControl(
    new NavigationControl({
      showCompass: false,
      showZoom: true,
    }),
    'bottom-right',
  );

  let points: MapPoint[] = [];
  let markers: Marker[] = [];
  let markerElements = new Map<number, HTMLButtonElement>();
  let selectedPopup: Popup | null = null;
  let hoverPopup: Popup | null = null;
  let selectedPointId: number | null = null;

  const removeSelectedPopup = () => {
    selectedPopup?.remove();
    selectedPopup = null;
  };

  const removeHoverPopup = () => {
    hoverPopup?.remove();
    hoverPopup = null;
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
      .setDOMContent(createPopupContent(point))
      .addTo(map);
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
      .setDOMContent(createPopupContent(point))
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
      element.addEventListener('mouseleave', removeHoverPopup);
      element.addEventListener('focus', () => showHoverPopup(point));
      element.addEventListener('blur', removeHoverPopup);
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
      ? { top: 96, right: 390, bottom: 96, left: 72 }
      : { top: 72, right: 24, bottom: Math.round(height * 0.5), left: 24 };
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
