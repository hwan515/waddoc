import { Navigation, Truck, Video, AlertOctagon, AlertTriangle } from 'lucide-react';
import MinimapPanel from './MinimapPanel';
import apiClient from '../../utils/api';

const formatSpeed = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value) || value <= 0) return '0 km/h';
    return `${value.toFixed(value >= 10 ? 0 : 1)} km/h`;
};

const formatCoordinate = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value)) return '-';
    return value.toFixed(4);
};

const getStatusBadge = (status) => {
    switch (status) {
        case '대기':
        case '대기 중':
            return 'bg-green-100 text-green-700 border-green-200';
        case '출발':
        case '출동':
        case '주행 중':
        case '이동 중':
            return 'bg-blue-100 text-blue-700 border-blue-200';
        case '도착':
        case '도착 완료':
            return 'bg-amber-100 text-amber-700 border-amber-200';
        case '진료 중':
        case '본인 확인':
            return 'bg-purple-100 text-purple-700 border-purple-200';
        case '복귀 중':
        case '종료/복귀':
            return 'bg-yellow-100 text-yellow-700 border-yellow-200';
        case '긴급 정지':
        case '긴급정지':
        case '이슈 발생':
            return 'bg-red-100 text-red-700 border-red-200';
        case '추후 서비스 예정':
            return 'bg-slate-100 text-slate-500 border-slate-200';
        default:
            return 'bg-slate-100 text-slate-700 border-slate-200';
    }
};

const MapMonitoring = ({
    vehicles,
    selectedVehicle,
    selectedVehicleId,
    setSelectedVehicleId,
    minimapVehiclePose,
    minimapPathPoints,
    minimapFullPathPoints,
    vehicleState = '대기',
    vehicleSpeed = 0,
    vehicleLocation = null,
    minimapRouteAlert = null,
    updateIntervalMs = 100,
    useMockMinimapData = false,
}) => {
    
    // E-Stop REST API POST 요청 핸들러
    const handleEStop = async () => {
        if (!window.confirm('정말로 E-Stop을 발동하시겠습니까?')) return;

        try {
            await apiClient.post('/robots/cmd/estop/1');
            window.alert('E-Stop 명령을 전송했습니다.');
        } catch (error) {
            console.error('E-Stop error:', error);
            window.alert('E-Stop 명령 전송에 실패했습니다.');
        }
    };

    const selectedVehicleLabel = selectedVehicle
        ? (selectedVehicle.vehicleId || selectedVehicle.id)
        : '차량을 선택하세요';

    return (
        <div className="flex h-full gap-4 p-4">
            <div className="relative min-w-0 flex-1 overflow-hidden rounded-4xl">
                {minimapRouteAlert && (
                    <div className="absolute left-4 top-4 z-10 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50/95 px-4 py-3 text-amber-900 shadow-lg shadow-amber-900/10 backdrop-blur-sm">
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                        <div className="min-w-0">
                            <div className="text-xs font-bold">{minimapRouteAlert.title}</div>
                            <div className="text-[11px] text-amber-800/80">{minimapRouteAlert.detail}</div>
                        </div>
                    </div>
                )}

                <div className="h-full overflow-hidden rounded-4xl">
                    <MinimapPanel
                        vehiclePose={minimapVehiclePose}
                        pathPoints={minimapPathPoints}
                        fullPathPoints={minimapFullPathPoints}
                        vehicleState={vehicleState}
                        vehicleSpeed={vehicleSpeed}
                        vehicleLocation={vehicleLocation}
                        updateIntervalMs={updateIntervalMs}
                        showMockBadge={useMockMinimapData}
                    />
                </div>
            </div>

            <div className="flex w-100 shrink-0 flex-col gap-4">
                <button
                    onClick={handleEStop}
                    className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-red-600 text-sm font-bold text-white shadow-lg shadow-red-600/30 transition-transform hover:bg-red-700 active:scale-100"
                >
                    <AlertOctagon className="h-5 w-5 animate-pulse" />
                    EMERGENCY STOP
                </button>

                <div className="flex flex-3 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                    <div className="flex h-14 shrink-0 items-center justify-between border-b border-slate-100 bg-slate-50/50 px-5">
                        <h3 className="flex items-center gap-2 text-base font-bold text-slate-800">
                            <Truck className="h-5 w-5 text-primary" /> 운영 차량 리스트
                        </h3>
                        <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-bold text-primary">
                            {vehicles.length}대
                        </span>
                    </div>

                    <div className="custom-scrollbar flex-1 space-y-2 overflow-y-auto p-3">
                        {vehicles.map((vehicle) => {
                            const isPrimaryServiceVehicle = Boolean(vehicle.isPrimaryServiceVehicle);
                            const isSelectedVehicle = vehicle.id === selectedVehicleId;
                            const displayStatus = isSelectedVehicle ? vehicleState || vehicle.status : vehicle.status;
                            const displaySpeed = isSelectedVehicle && typeof vehicleSpeed === 'number'
                                ? vehicleSpeed
                                : vehicle.speed;
                            const displayLocation = isPrimaryServiceVehicle ? vehicle.location : null;
                            const displayBattery = typeof vehicle.battery === 'number' ? `${vehicle.battery}%` : '-';

                            return (
                                <div
                                    key={vehicle.id}
                                    onClick={isPrimaryServiceVehicle ? () => setSelectedVehicleId(vehicle.id) : undefined}
                                    className={`rounded-xl border p-3 transition-all ${isSelectedVehicle
                                        ? 'cursor-pointer border-primary bg-primary/5 shadow-sm'
                                        : isPrimaryServiceVehicle
                                            ? 'cursor-pointer border-slate-200 hover:border-accent-1/30 hover:bg-slate-50'
                                            : 'cursor-default border-slate-200 bg-slate-50/80 opacity-75'
                                        }`}
                                >
                                    <div className="mb-2 flex items-start justify-between gap-3">
                                        <div className="min-w-0">
                                            <div className="text-sm font-bold text-slate-800">{vehicle.vehicleId || vehicle.id}</div>
                                            {vehicle.displayPatientName && (
                                                <div className="truncate text-[11px] text-slate-500">{vehicle.displayPatientName}</div>
                                            )}
                                            {vehicle.displayDestination && (
                                                <div className="truncate text-[11px] text-slate-400">{vehicle.displayDestination}</div>
                                            )}
                                        </div>
                                        <div className={`shrink-0 rounded-md border px-2 py-0.5 text-xs font-bold ${getStatusBadge(displayStatus)}`}>
                                            {displayStatus}
                                        </div>
                                    </div>

                                    <div className="space-y-1.5 text-xs font-medium text-slate-600">
                                        <div className="flex items-center gap-2">
                                            <Navigation className="h-3.5 w-3.5 text-slate-400" />
                                            <span className="font-mono">
                                                {formatCoordinate(displayLocation?.lat)}, {formatCoordinate(displayLocation?.lng)}
                                            </span>
                                        </div>
                                        <div className="mt-2 flex items-center justify-between border-t border-slate-200/60 pt-2">
                                            <span className="flex items-center gap-1.5">
                                                배터리 <span className="font-bold text-slate-800">{displayBattery}</span>
                                            </span>
                                            <span className="flex items-center gap-1.5">
                                                속도 <span className="font-bold text-slate-800">{formatSpeed(displaySpeed)}</span>
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                <div className="relative flex flex-2 flex-col overflow-hidden rounded-4xl border border-slate-800 bg-slate-950 shadow-sm">
                    <div className="absolute left-3 top-3 z-10 flex items-center gap-2 rounded-xl border border-white/10 bg-black/55 px-3 py-1.5 text-xs font-bold text-white backdrop-blur-sm">
                        <Video className="h-3.5 w-3.5 text-red-400" />
                        {selectedVehicleLabel}
                        <span className="ml-1 h-1.5 w-1.5 animate-pulse rounded-full bg-red-500"></span>
                    </div>

                    <div className="relative flex flex-1 items-center justify-center overflow-hidden rounded-4xl">
                        <div className="absolute inset-0 z-20 h-full w-full overflow-hidden rounded-4xl bg-black">
                            <iframe
                                src="/unity_cam/"
                                title="Camera stream"
                                className="h-full w-full rounded-4xl border-none"
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                                allowFullScreen
                            ></iframe>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default MapMonitoring;
