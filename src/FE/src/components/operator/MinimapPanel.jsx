import { useState } from 'react';
import { Activity, AlertTriangle, Gauge, Navigation, Radio } from 'lucide-react';
import { pathPointsToMinimap, vehiclePoseToMinimap } from '../../utils/worldToMinimap';

const VIEWBOX_WIDTH = 1000;
const VIEWBOX_HEIGHT = 1000;
const BACKGROUND_CONTENT_SCALE = 1.12;
const BACKGROUND_CONTENT_OFFSET_X = VIEWBOX_WIDTH * (1 - BACKGROUND_CONTENT_SCALE);
const BACKGROUND_CONTENT_OFFSET_Y = (VIEWBOX_HEIGHT * (1 - BACKGROUND_CONTENT_SCALE)) / 2;
const VEHICLE_BADGE_SRC = '/waddoc-badge-primary.svg';
const VEHICLE_MARKER_SIZE = 42;
const VEHICLE_HALO_RADIUS = 28;
const START_MARKER_RADIUS = 5;
const START_MARKER_STROKE_WIDTH = 1.5;
const FLAG_POLE_HEIGHT = 26;
const FLAG_MARKER_OVERLAP_THRESHOLD = 12;

const UNITY_MINIMAP_OPTIONS = {
    origin: 'bottom-left',
    invertY: true
};

const isSameMinimapPoint = (firstPoint, secondPoint, threshold = FLAG_MARKER_OVERLAP_THRESHOLD) => {
    if (!firstPoint || !secondPoint) {
        return false;
    }

    const dx = firstPoint.x - secondPoint.x;
    const dy = firstPoint.y - secondPoint.y;
    return (dx * dx) + (dy * dy) <= (threshold * threshold);
};

const formatCoordinate = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value)) return '-';
    return value.toFixed(4);
};

const formatSpeed = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value) || value <= 0) return '0 km/h';
    return `${value.toFixed(value >= 10 ? 0 : 1)} km/h`;
};

const formatUpdateInterval = (value) => {
    if (value == null) return '실시간';
    if (typeof value !== 'number' || Number.isNaN(value)) return '-';
    if (value >= 1000) {
        return `${(value / 1000).toFixed(value % 1000 === 0 ? 0 : 1)} sec`;
    }
    return `${value} ms`;
};

const normalizeState = (state) => {
    if (typeof state !== 'string') return '대기';

    const trimmed = state.trim();
    if (!trimmed) return '대기';

    if (trimmed === 'CREATED') return '대기';
    if (['WAITING', 'STANDBY', 'IDLE'].includes(trimmed.toUpperCase())) return '대기';

    return trimmed;
};

const getStatePresentation = (state) => {
    const normalizedState = normalizeState(state);

    switch (normalizedState) {
        case '대기':
        case '대기 중':
            return {
                label: '대기',
                chipClass: 'border-emerald-300/30 bg-emerald-400/12 text-emerald-100',
                dotClass: 'bg-emerald-300'
            };
        case '출발':
        case '출동':
            return {
                label: '출동',
                chipClass: 'border-emerald-300/30 bg-emerald-400/12 text-emerald-100',
                dotClass: 'bg-emerald-300'
            };
        case '주행 중':
        case '이동 중':
            return {
                label: '주행 중',
                chipClass: 'border-sky-300/30 bg-sky-400/12 text-sky-100',
                dotClass: 'bg-sky-300'
            };
        case '도착':
        case '도착 완료':
            return {
                label: '도착',
                chipClass: 'border-amber-300/30 bg-amber-400/12 text-amber-100',
                dotClass: 'bg-amber-300'
            };
        case '진료 중':
        case '본인 확인':
            return {
                label: normalizedState,
                chipClass: 'border-amber-300/30 bg-amber-400/12 text-amber-100',
                dotClass: 'bg-amber-300'
            };
        case '긴급 정지':
        case '긴급정지':
        case '이슈 발생':
            return {
                label: normalizedState,
                chipClass: 'border-rose-300/30 bg-rose-400/12 text-rose-100',
                dotClass: 'bg-rose-300'
            };
        default:
            return {
                label: normalizedState,
                chipClass: 'border-slate-300/20 bg-white/10 text-slate-100',
                dotClass: 'bg-slate-300'
            };
    }
};

const StatCard = ({ icon, label, children }) => {
    const IconComponent = icon;

    return (
        <div className="rounded-[1.75rem] border border-white/12 bg-[linear-gradient(135deg,rgba(18,37,62,0.92),rgba(56,62,36,0.42))] px-4 py-3.5 shadow-[0_16px_32px_rgba(2,8,23,0.24)] backdrop-blur-md">
            <div className="flex items-center gap-2.5 text-base font-black tracking-tight text-slate-50 xl:text-[1.6rem]">
                <IconComponent className="h-5 w-5 text-slate-200 xl:h-6 xl:w-6" />
                {label}
            </div>
            {children}
        </div>
    );
};

const CoordinateRow = ({ axis, value }) => (
    <div className="flex items-center justify-between rounded-2xl bg-white/5 px-3 py-2">
        <span className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">{axis}</span>
        <span className="text-sm font-semibold text-white">{value}</span>
    </div>
);

const PANEL_LAYOUT_STYLE = {
    '--monitor-card-width': 'clamp(11.75rem, 19vw, 17rem)'
};

const MinimapPanel = ({
    vehiclePose,
    pathPoints = [],
    fullPathPoints = [],
    worldWidth = 4000,
    worldHeight = 4000,
    imageSrc = '/minimap.png',
    vehicleState = '대기',
    vehicleSpeed = 0,
    vehicleLocation = null,
    updateIntervalMs = null,
    routeAlert = null,
    showMockBadge = false
}) => {
    const [imageReady, setImageReady] = useState(true);

    const minimapVehiclePose = vehiclePoseToMinimap(
        vehiclePose,
        worldWidth,
        worldHeight,
        VIEWBOX_WIDTH,
        VIEWBOX_HEIGHT,
        UNITY_MINIMAP_OPTIONS
    );

    const minimapPathPoints = pathPointsToMinimap(
        pathPoints,
        worldWidth,
        worldHeight,
        VIEWBOX_WIDTH,
        VIEWBOX_HEIGHT,
        UNITY_MINIMAP_OPTIONS
    );
    const minimapFullPathPoints = pathPointsToMinimap(
        fullPathPoints,
        worldWidth,
        worldHeight,
        VIEWBOX_WIDTH,
        VIEWBOX_HEIGHT,
        UNITY_MINIMAP_OPTIONS
    );

    const polylinePoints = minimapPathPoints.map((point) => `${point.x},${point.y}`).join(' ');
    const fullPolylinePoints = minimapFullPathPoints.map((point) => `${point.x},${point.y}`).join(' ');
    const statePresentation = getStatePresentation(vehicleState);
    const hasActivePath = minimapPathPoints.length > 1;
    const startMarkerPoint = hasActivePath ? minimapPathPoints[0] : null;
    const endMarkerPoint = hasActivePath ? minimapPathPoints[minimapPathPoints.length - 1] : null;
    const shouldRenderStartMarker = hasActivePath && !isSameMinimapPoint(startMarkerPoint, endMarkerPoint);

    return (
        <div
            className="relative h-full min-w-0 overflow-hidden rounded-[2.5rem] bg-[#03152F] shadow-[0_20px_55px_rgba(3,26,64,0.22)]"
            style={PANEL_LAYOUT_STYLE}
        >
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(96,165,250,0.28),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.14),transparent_24%),linear-gradient(180deg,#082041_0%,#04142B_58%,#020817_100%)]" />

            <div className="absolute inset-0 z-0 overflow-hidden rounded-[2.5rem]">
                <img
                    src={imageSrc}
                    alt=""
                    aria-hidden="true"
                    className="absolute h-0 w-0 opacity-0 pointer-events-none"
                    onError={() => setImageReady(false)}
                />

                <svg
                    viewBox={`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`}
                    className="h-full w-full"
                    preserveAspectRatio="xMaxYMid meet"
                >
                    <g transform={`translate(${BACKGROUND_CONTENT_OFFSET_X} ${BACKGROUND_CONTENT_OFFSET_Y}) scale(${BACKGROUND_CONTENT_SCALE})`}>
                        {imageReady && (
                            <image
                                href={imageSrc}
                                x="0"
                                y="0"
                                width={VIEWBOX_WIDTH}
                                height={VIEWBOX_HEIGHT}
                                preserveAspectRatio="xMidYMid meet"
                            />
                        )}

                        {minimapFullPathPoints.length > 1 && (
                            <polyline
                                points={fullPolylinePoints}
                                fill="none"
                                stroke="rgba(148, 163, 184, 0.72)"
                                strokeWidth="6"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                            />
                        )}

                        {hasActivePath && (
                            <>
                                <polyline
                                    points={polylinePoints}
                                    fill="none"
                                    stroke="rgba(14, 165, 233, 0.28)"
                                    strokeWidth="16"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                />
                                <polyline
                                    points={polylinePoints}
                                    fill="none"
                                    stroke="#38BDF8"
                                    strokeWidth="6"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                />
                                {shouldRenderStartMarker && (
                                    <circle
                                        cx={startMarkerPoint.x}
                                        cy={startMarkerPoint.y}
                                        r={START_MARKER_RADIUS}
                                        fill="rgba(34, 197, 94, 0.42)"
                                        stroke="rgba(255, 255, 255, 0.35)"
                                        strokeWidth={START_MARKER_STROKE_WIDTH}
                                    />
                                )}
                                {endMarkerPoint && (
                                    <g transform={`translate(${endMarkerPoint.x} ${endMarkerPoint.y})`}>
                                        <line
                                            x1="0"
                                            y1="10"
                                            x2="0"
                                            y2={-FLAG_POLE_HEIGHT}
                                            stroke="rgba(226, 232, 240, 0.92)"
                                            strokeWidth="3"
                                            strokeLinecap="round"
                                        />
                                        <path
                                            d={`M 0 ${-FLAG_POLE_HEIGHT} L 16 ${-FLAG_POLE_HEIGHT + 5} L 0 ${-FLAG_POLE_HEIGHT + 11} Z`}
                                            fill="#F59E0B"
                                            stroke="rgba(255, 255, 255, 0.85)"
                                            strokeWidth="1.5"
                                            strokeLinejoin="round"
                                        />
                                        <circle
                                            cx="0"
                                            cy="10"
                                            r="3"
                                            fill="rgba(245, 158, 11, 0.92)"
                                            stroke="rgba(255, 255, 255, 0.7)"
                                            strokeWidth="1.5"
                                        />
                                    </g>
                                )}
                            </>
                        )}

                        {minimapVehiclePose && (
                            <g transform={`translate(${minimapVehiclePose.x} ${minimapVehiclePose.y})`}>
                                <circle r={VEHICLE_HALO_RADIUS} fill="rgba(220, 38, 38, 0.16)" />
                                <image
                                    href={VEHICLE_BADGE_SRC}
                                    x={-(VEHICLE_MARKER_SIZE / 2)}
                                    y={-(VEHICLE_MARKER_SIZE / 2)}
                                    width={VEHICLE_MARKER_SIZE}
                                    height={VEHICLE_MARKER_SIZE}
                                    preserveAspectRatio="xMidYMid meet"
                                />
                            </g>
                        )}
                    </g>
                </svg>

                <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(7,26,55,0.86)_0%,rgba(7,26,55,0.68)_24%,rgba(7,26,55,0.26)_42%,rgba(7,26,55,0.08)_56%,transparent_68%),linear-gradient(180deg,rgba(2,8,23,0.08)_0%,transparent_54%,rgba(2,8,23,0.32)_100%)]" />

                {routeAlert && (
                    <div className="absolute right-4 top-4 z-20 flex max-w-[min(18rem,calc(100%-2rem))] items-start gap-3 rounded-2xl border border-amber-200/80 bg-amber-50/94 px-4 py-3 text-amber-900 shadow-lg shadow-amber-900/10 backdrop-blur-sm">
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                        <div className="min-w-0">
                            <div className="text-xs font-bold">{routeAlert.title}</div>
                            <div className="truncate text-[11px] text-amber-800/80">{routeAlert.detail}</div>
                        </div>
                    </div>
                )}

                {!imageReady && (
                    <div className="absolute inset-0 flex items-center justify-center bg-[#0F172A] text-center text-sm text-slate-300">
                        <div>
                            <p className="font-semibold">`public/minimap.png`를 찾을 수 없습니다.</p>
                            <p className="mt-1 text-xs text-slate-400">이미지를 복사하면 실제 미니맵 배경으로 교체됩니다.</p>
                        </div>
                    </div>
                )}

                {!minimapVehiclePose && (
                    <div className="absolute bottom-4 right-4 rounded-2xl border border-white/10 bg-dark/72 px-4 py-3 text-sm text-white shadow-lg backdrop-blur-sm">
                        odom 위치 데이터가 연결되면 차량 마커가 표시됩니다.
                    </div>
                )}
            </div>

            <div className="absolute left-4 top-4 z-20 flex w-[min(var(--monitor-card-width),calc(100%-2rem))] flex-col gap-4 sm:left-6 sm:top-6">
                {showMockBadge && (
                    <div className="inline-flex w-fit rounded-full border border-cyan-300/20 bg-cyan-400/10 px-3 py-1 text-xs font-semibold text-cyan-100">
                        Mock
                    </div>
                )}

                <StatCard icon={Activity} label="운행 상태">
                    <div className={`mt-3.5 inline-flex items-center gap-2.5 rounded-full border px-4 py-2.5 text-xl font-black ${statePresentation.chipClass}`}>
                        <span className={`h-3 w-3 rounded-full ${statePresentation.dotClass}`}></span>
                        {statePresentation.label}
                    </div>
                    <p className="mt-4 text-base font-semibold text-slate-300">실시간 상태 반영</p>
                </StatCard>

                <StatCard icon={Gauge} label="차량 속도">
                    <p className="mt-3.5 text-3xl leading-none font-black text-white xl:text-[3.15rem]">{formatSpeed(vehicleSpeed)}</p>
                    <p className="mt-4 text-base font-semibold text-slate-300">정지 시 0 km/h</p>
                </StatCard>

                <StatCard icon={Radio} label="업데이트 주기">
                    <p className="mt-3.5 text-3xl leading-none font-black text-white xl:text-[3.15rem]">{formatUpdateInterval(updateIntervalMs)}</p>
                    <p className="mt-4 text-base font-semibold text-slate-300">SSE 실시간 스트림 기준</p>
                </StatCard>

                <StatCard icon={Navigation} label="실시간 좌표">
                    <div className="mt-3.5 space-y-2">
                        <CoordinateRow axis={vehicleLocation?.latLabel || '위도'} value={formatCoordinate(vehicleLocation?.lat)} />
                        <CoordinateRow axis={vehicleLocation?.lngLabel || '경도'} value={formatCoordinate(vehicleLocation?.lng)} />
                    </div>
                    <p className="mt-3.5 text-xs font-medium text-slate-300">
                        {vehicleLocation?.source === 'pose' ? 'GPS 미수신 시 맵 좌표 표시' : '차량 리스트와 동일'}
                    </p>
                </StatCard>
            </div>
        </div>
    );
};

export default MinimapPanel;
