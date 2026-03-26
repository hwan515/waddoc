import { useState } from 'react';
import { Activity, Gauge, Navigation, Radio } from 'lucide-react';
import { pathPointsToMinimap, vehiclePoseToMinimap } from '../../utils/worldToMinimap';

const VIEWBOX_WIDTH = 1000;
const VIEWBOX_HEIGHT = 1000;
const UNITY_MINIMAP_OPTIONS = {
    origin: 'bottom-left',
    invertY: true
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
        <div className="rounded-3xl border border-white/10 bg-white/5 px-4 py-3 shadow-lg backdrop-blur-md">
            <div className="flex items-center gap-2 text-sm font-semibold uppercase tracking-[0.16em] text-slate-200">
                <IconComponent className="h-4 w-4 text-secondary" />
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

const MinimapPanel = ({
    vehiclePose,
    pathPoints = [],
    worldWidth = 4000,
    worldHeight = 4000,
    imageSrc = '/minimap.png',
    vehicleState = '대기',
    vehicleSpeed = 0,
    vehicleLocation = null,
    updateIntervalMs = 100,
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

    const polylinePoints = minimapPathPoints.map((point) => `${point.x},${point.y}`).join(' ');
    const statePresentation = getStatePresentation(vehicleState);

    return (
        <div className="relative h-full overflow-hidden rounded-4xl bg-[#03152F] shadow-[0_20px_55px_rgba(3,26,64,0.22)]">
            <div className="absolute inset-0 rounded-4xl bg-[radial-gradient(circle_at_top_left,rgba(96,165,250,0.28),transparent_30%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.14),transparent_24%),linear-gradient(180deg,#082041_0%,#04142B_58%,#020817_100%)]" />
            <div className="absolute inset-y-0 left-0 w-[28%] min-w-52.5 max-w-62.5 rounded-l-4xl bg-linear-to-r from-[#03152F]/96 via-[#03152F]/80 to-transparent" />

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

            <div className="absolute inset-0 rounded-4xl bg-linear-to-b from-dark/10 via-transparent to-[#020817]/38" />

            <svg
                viewBox={`0 0 ${VIEWBOX_WIDTH} ${VIEWBOX_HEIGHT}`}
                className="absolute inset-0 h-full w-full rounded-4xl"
                preserveAspectRatio="xMaxYMid meet"
            >
                {minimapPathPoints.length > 1 && (
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
                        <circle
                            cx={minimapPathPoints[0].x}
                            cy={minimapPathPoints[0].y}
                            r="10"
                            fill="#22C55E"
                            stroke="white"
                            strokeWidth="3"
                        />
                        <circle
                            cx={minimapPathPoints[minimapPathPoints.length - 1].x}
                            cy={minimapPathPoints[minimapPathPoints.length - 1].y}
                            r="10"
                            fill="#F59E0B"
                            stroke="white"
                            strokeWidth="3"
                        />
                    </>
                )}

                {minimapVehiclePose && (
                    <g transform={`translate(${minimapVehiclePose.x} ${minimapVehiclePose.y})`}>
                        <circle r="24" fill="rgba(220, 38, 38, 0.16)" />
                        <circle r="12" fill="#DC2626" stroke="white" strokeWidth="4" />
                    </g>
                )}
            </svg>

            <div className="relative z-10 flex h-full p-4 xl:p-5">
                <div className="w-52.5 space-y-3 xl:w-60">
                    {showMockBadge && (
                        <div className="inline-flex rounded-full border border-cyan-300/20 bg-cyan-400/10 px-3 py-1 text-xs font-semibold text-cyan-100">
                            Mock
                        </div>
                    )}

                    <StatCard icon={Activity} label="운행 상태">
                        <div className={`mt-3 inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-semibold ${statePresentation.chipClass}`}>
                            <span className={`h-2 w-2 rounded-full ${statePresentation.dotClass}`}></span>
                            {statePresentation.label}
                        </div>
                        <p className="mt-3 text-xs text-slate-400">실시간 상태 반영</p>
                    </StatCard>

                    <StatCard icon={Gauge} label="차량 속도">
                        <p className="mt-3 text-2xl font-semibold text-white">{formatSpeed(vehicleSpeed)}</p>
                        <p className="mt-1 text-xs text-slate-400">정지 시 0 km/h</p>
                    </StatCard>

                    <StatCard icon={Radio} label="업데이트 주기">
                        <p className="mt-3 text-2xl font-semibold text-white">{formatUpdateInterval(updateIntervalMs)}</p>
                        <p className="mt-1 text-xs text-slate-400">미니맵 폴링 간격</p>
                    </StatCard>

                    <StatCard icon={Navigation} label="실시간 좌표">
                        <div className="mt-3 space-y-2">
                            <CoordinateRow axis="위도" value={formatCoordinate(vehicleLocation?.lat)} />
                            <CoordinateRow axis="경도" value={formatCoordinate(vehicleLocation?.lng)} />
                        </div>
                        <p className="mt-3 text-xs text-slate-400">차량 리스트와 동일</p>
                    </StatCard>
                </div>
            </div>

            {!minimapVehiclePose && (
                <div className="absolute bottom-4 left-[calc(28%+1rem)] rounded-2xl border border-white/10 bg-dark/72 px-4 py-3 text-sm text-white shadow-lg backdrop-blur-sm">
                    odom 위치 데이터가 연결되면 차량 마커가 표시됩니다.
                </div>
            )}
        </div>
    );
};

export default MinimapPanel;
