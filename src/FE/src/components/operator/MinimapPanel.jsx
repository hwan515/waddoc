import { useState } from 'react';
import { Activity, Gauge, Navigation } from 'lucide-react';
import { pathPointsToMinimap, vehiclePoseToMinimap } from '../../utils/worldToMinimap';

const VIEWBOX_WIDTH = 1000;
const VIEWBOX_HEIGHT = 1000;
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
                chipClass: 'border-violet-300/30 bg-violet-400/12 text-violet-100',
                dotClass: 'bg-violet-300'
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
        <div className="flex min-h-[9.5rem] flex-1 flex-col rounded-3xl border border-white/10 bg-white/5 px-4 py-3 shadow-lg backdrop-blur-md">
            <div className="flex items-center gap-2.5 text-base font-semibold uppercase tracking-[0.14em] text-slate-100">
                <IconComponent className="h-[1.125rem] w-[1.125rem] text-secondary" />
                {label}
            </div>
            <div className="flex flex-1 flex-col">
                {children}
            </div>
        </div>
    );
};

const CoordinateRow = ({ axis, value }) => (
    <div className="flex items-center justify-between rounded-2xl bg-white/5 px-3.5 py-2.5">
        <span className="text-sm font-semibold uppercase tracking-[0.14em] text-slate-300">{axis}</span>
        <span className="text-xl font-semibold text-white">{value}</span>
    </div>
);

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
        <div className="relative h-full overflow-hidden rounded-4xl bg-[#03152F] shadow-[0_20px_55px_rgba(3,26,64,0.22)]">
            <div className="absolute inset-0 rounded-4xl bg-[radial-gradient(circle_at_top_left,rgba(96,165,250,0.28),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.14),transparent_24%),linear-gradient(180deg,#082041_0%,#04142B_58%,#020817_100%)]" />

            {imageReady ? (
                <img
                    src={imageSrc}
                    alt="Unity minimap"
                    className="absolute inset-0 h-full w-full select-none rounded-4xl object-contain object-right"
                    draggable="false"
                    onError={() => setImageReady(false)}
                />
            ) : (
                <div className="absolute inset-0 flex items-center justify-center rounded-4xl bg-[#0F172A] text-center text-sm text-slate-300">
                    <div>
                        <p className="font-semibold">`public/minimap.png`를 찾을 수 없습니다.</p>
                        <p className="mt-1 text-xs text-slate-400">이미지를 복사하면 실제 미니맵 배경으로 교체됩니다.</p>
                    </div>
                </div>
            )}

            <div className="absolute inset-y-0 left-0 w-[17.5rem] xl:w-[19.5rem] rounded-l-4xl bg-[linear-gradient(90deg,rgba(3,21,47,0.99)_0%,rgba(3,21,47,0.96)_68%,rgba(4,26,58,0.7)_84%,transparent_100%)]" />
            <div className="absolute inset-0 rounded-4xl bg-linear-to-b from-dark/10 via-transparent to-[#020817]/38" />

            <svg
                viewBox={`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`}
                className="absolute inset-0 h-full w-full rounded-4xl"
                preserveAspectRatio="xMaxYMid meet"
            >
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
            </svg>

            <div className="relative z-10 flex h-full p-4 xl:p-5">
                <div className="flex h-full w-[16.5rem] flex-col xl:w-[18rem]">
                    {showMockBadge && (
                        <div className="inline-flex rounded-full border border-cyan-300/20 bg-cyan-400/10 px-3 py-1 text-xs font-semibold text-cyan-100">
                            Mock
                        </div>
                    )}

                    <div className={`flex flex-1 flex-col gap-3 ${showMockBadge ? 'mt-3' : ''}`}>
                        <StatCard icon={Activity} label="운행 상태">
                            <div className="flex flex-1 flex-col justify-center">
                                <div className={`inline-flex self-start items-center gap-2 rounded-full border px-3.5 py-2 text-base font-semibold ${statePresentation.chipClass}`}>
                                    <span className={`h-2.5 w-2.5 rounded-full ${statePresentation.dotClass}`}></span>
                                    {statePresentation.label}
                                </div>
                                <p className="mt-3 text-sm text-slate-300">실시간 상태 반영</p>
                            </div>
                        </StatCard>

                        <StatCard icon={Gauge} label="차량 속도">
                            <div className="flex flex-1 flex-col justify-center">
                                <p className="text-[2.2rem] font-semibold leading-none text-white">{formatSpeed(vehicleSpeed)}</p>
                                <p className="mt-2 text-sm text-slate-300">정지 시 0 km/h</p>
                            </div>
                        </StatCard>

                        <StatCard icon={Navigation} label="실시간 좌표">
                            <div className="mt-3 space-y-2">
                                <CoordinateRow axis="위도" value={formatCoordinate(vehicleLocation?.lat)} />
                                <CoordinateRow axis="경도" value={formatCoordinate(vehicleLocation?.lng)} />
                            </div>
                            <p className="mt-3 text-sm text-slate-300">차량 리스트와 동일</p>
                        </StatCard>
                    </div>
                </div>
            </div>

            {!minimapVehiclePose && (
                <div className="absolute bottom-4 left-[17.75rem] rounded-2xl border border-white/10 bg-dark/72 px-4 py-3 text-sm text-white shadow-lg backdrop-blur-sm xl:left-[19.75rem]">
                    odom 위치 데이터가 연결되면 차량 마커가 표시됩니다.
                </div>
            )}
        </div>
    );
};

export default MinimapPanel;
