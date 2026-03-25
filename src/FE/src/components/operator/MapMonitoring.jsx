import { Navigation, Truck, Video, AlertOctagon } from 'lucide-react';
import MinimapPanel from './MinimapPanel';
import { getRobotCommandUrlCandidates } from '../../utils/runtimeConfig';

const formatSpeed = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value) || value <= 0) return '0 km/h';
    return `${value.toFixed(value >= 10 ? 0 : 1)} km/h`;
};

const formatCoordinate = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value)) return '-';
    return value.toFixed(4);
};

const MapMonitoring = ({
    vehicles,
    selectedVehicleId,
    setSelectedVehicleId,
    minimapVehiclePose,
    minimapPathPoints,
    vehicleState = '대기',
    vehicleSpeed = 0,
    vehicleLocation = null,
    updateIntervalMs = 100,
    useMockMinimapData = false,
}) => {
    // E-Stop REST API POST 요청 핸들러
    const handleEStop = async () => {
        if (!window.confirm("정말로 E-Stop (긴급 정지)을 작동하시겠습니까?")) return;

        try {
            let success = false;
            let lastError = null;

            for (const targetUrl of getRobotCommandUrlCandidates('/api/cmd/estop/1')) {
                try {
                    const response = await fetch(targetUrl, {
                        method: 'POST',
                        headers: { 'accept': 'application/json' }
                    });

                    if (response.ok) {
                        success = true;
                        break;
                    }

                    lastError = new Error(`E-Stop API error: ${response.status}`);
                } catch (error) {
                    lastError = error;
                }
            }

            if (success) {
                alert("E-Stop 명령이 성공적으로 전송되었습니다.");
            } else {
                throw lastError || new Error('E-Stop API unavailable');
            }
        } catch (error) {
            console.error("E-Stop Error:", error);
            alert("E-Stop 명령 전송에 실패했습니다.");
        }
    };

    // 상태에 따른 배지 색상 결정 헬퍼 함수
    const getStatusBadge = (status) => {
        switch (status) {
            case '출발':
            case '주행 중':
            case '운행 중': return 'bg-blue-100 text-blue-700 border-blue-200';
            case '대기':
            case '대기 중': return 'bg-green-100 text-green-700 border-green-200';
            case '도착': return 'bg-amber-100 text-amber-700 border-amber-200';
            case '진료중':
            case '진료 중': return 'bg-purple-100 text-purple-700 border-purple-200';
            case '점검 중': return 'bg-yellow-100 text-yellow-700 border-yellow-200';
            case '긴급 정지':
            case '긴급정지':
            case '장애': return 'bg-red-100 text-red-700 border-red-200';
            default: return 'bg-slate-100 text-slate-700 border-slate-200';
        }
    };

    return (
        <div className="h-full flex p-4 gap-4">
            <div className="relative min-w-0 flex-1 overflow-hidden rounded-4xl">
                <div className="h-full overflow-hidden rounded-4xl">
                    <MinimapPanel
                        vehiclePose={minimapVehiclePose}
                        pathPoints={minimapPathPoints}
                        vehicleState={vehicleState}
                        vehicleSpeed={vehicleSpeed}
                        vehicleLocation={vehicleLocation}
                        updateIntervalMs={updateIntervalMs}
                        showMockBadge={useMockMinimapData}
                    />
                </div>
            </div>

            {/* 우측: 사이드 패널 (E-Stop + 차량 리스트 + 카메라) */}
            <div className="w-100 flex flex-col gap-4 shrink-0">
                {/* 1. 상단: E-Stop 버튼 (최소한의 높이 h-12 고정, 너비 가득 참) */}
                <button
                    onClick={handleEStop}
                    className="w-full h-12 shrink-0 bg-red-600 hover:bg-red-700 text-white text-sm font-bold rounded-xl shadow-[0_4px_14px_0_rgba(220,38,38,0.39)] flex items-center justify-center gap-2 transition-transform active:scale-[0.98]"
                >
                    <AlertOctagon className="w-5 h-5 animate-pulse" />
                    EMERGENCY STOP (긴급 정지)
                </button>

                {/* 2. 중단: 차량 리스트 */}
                <div className="flex-[3] bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                    <div className="h-14 border-b border-slate-100 flex items-center justify-between px-5 bg-slate-50/50 shrink-0">
                        <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                            <Truck className="w-5 h-5 text-primary" /> 운영 차량 리스트
                        </h3>
                        <span className="bg-primary/10 text-primary px-2.5 py-1 rounded-full text-xs font-bold">
                            {vehicles.length}대
                        </span>
                    </div>
                    <div className="flex-1 overflow-y-auto p-3 space-y-2 custom-scrollbar">
                        {vehicles.map((vehicle) => {
                            const displayStatus = vehicle.id === selectedVehicleId ? vehicleState || vehicle.status : vehicle.status;
                            const displaySpeed = vehicle.id === selectedVehicleId && typeof vehicleSpeed === 'number'
                                ? vehicleSpeed
                                : vehicle.speed;

                            return (
                                <div
                                    key={vehicle.id}
                                    onClick={() => setSelectedVehicleId(vehicle.id)}
                                    className={`p-3 rounded-xl border cursor-pointer transition-all ${selectedVehicleId === vehicle.id
                                        ? 'border-primary bg-primary/5 shadow-sm'
                                        : 'border-slate-200 hover:border-accent-1/30 hover:bg-slate-50'
                                        }`}
                                >
                                    <div className="flex items-center justify-between mb-2">
                                        <div className="font-bold text-slate-800 text-sm">{vehicle.id}</div>
                                        <div className={`text-xs px-2 py-0.5 rounded-md border font-bold ${getStatusBadge(displayStatus)}`}>
                                            {displayStatus}
                                        </div>
                                    </div>
                                    <div className="space-y-1.5 text-xs text-slate-600 font-medium">
                                        <div className="flex items-center gap-2">
                                            <Navigation className="w-3.5 h-3.5 text-slate-400" />
                                            <span className="font-mono">
                                                {formatCoordinate(vehicle.location?.lat)}, {formatCoordinate(vehicle.location?.lng)}
                                            </span>
                                        </div>
                                        <div className="flex items-center justify-between mt-2 pt-2 border-t border-slate-200/60">
                                            <span className="flex items-center gap-1.5">
                                                배터리 <span className="font-bold text-slate-800">{vehicle.battery}%</span>
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

                <div className="flex-[2] bg-slate-950 rounded-4xl shadow-sm border border-slate-800 overflow-hidden relative flex flex-col">
                    <div className="absolute top-3 left-3 z-10 bg-black/55 backdrop-blur-sm px-3 py-1.5 rounded-xl text-white text-xs font-bold flex items-center gap-2 border border-white/10">
                        <Video className="w-3.5 h-3.5 text-red-400" />
                        {selectedVehicleId ? `${selectedVehicleId} 카메라` : '차량을 선택하세요'}
                        <span className="ml-1 w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse"></span>
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
