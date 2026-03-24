import { Map as MapIcon, Navigation, Truck, Video, AlertOctagon } from 'lucide-react';

const MapMonitoring = ({ vehicles, selectedVehicleId, setSelectedVehicleId }) => {
    // E-Stop REST API POST 요청 핸들러
    const handleEStop = async () => {
        if (!window.confirm("정말로 E-Stop (긴급 정지)을 작동하시겠습니까?")) return;

        try {
            // Zenoh-server API E-Stop 요청 (Nginx 프록시를 통해 포트 8000으로 전달됨)
            const targetUrl = `/api/cmd/estop/1`;
            const response = await fetch(targetUrl, {
                method: 'POST',
                headers: { 'accept': 'application/json' }
            });

            if (response.ok) {
                alert("E-Stop 명령이 성공적으로 전송되었습니다.");
            } else {
                alert("E-Stop 전송 중 오류가 발생했습니다.");
            }
        } catch (error) {
            console.error("E-Stop Error:", error);
            alert("E-Stop 명령 전송에 실패했습니다.");
        }
    };

    // 상태에 따른 배지 색상 결정 헬퍼 함수
    const getStatusBadge = (status) => {
        switch (status) {
            case '운행 중': return 'bg-blue-100 text-blue-700 border-blue-200';
            case '대기 중': return 'bg-green-100 text-green-700 border-green-200';
            case '진료 중': return 'bg-purple-100 text-purple-700 border-purple-200';
            case '점검 중': return 'bg-yellow-100 text-yellow-700 border-yellow-200';
            case '장애': return 'bg-red-100 text-red-700 border-red-200';
            default: return 'bg-slate-100 text-slate-700 border-slate-200';
        }
    };

    return (
        <div className="h-full flex p-4 gap-4">
            {/* 좌측: 디지털 트윈 (전체 맵 영역) */}
            <div className="flex-1 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col relative">
                <div className="absolute top-4 left-4 z-10 bg-white/90 backdrop-blur-sm px-4 py-2 rounded-lg shadow border border-slate-200">
                    <h2 className="font-bold text-slate-800 flex items-center gap-2">
                        <MapIcon className="w-4 h-4 text-[#006DAA]" /> 디지털 트윈 모니터링
                    </h2>
                    <p className="text-xs text-slate-500 mt-1">평소: 전체 Map / 차량 선택 시: 해당 차량 중심 뷰</p>
                </div>

                {/* TODO: Three.js 또는 카카오/네이버 지도 연동 영역 */}
                <div className="flex-1 bg-[#E8F0F8] flex items-center justify-center relative">
                    <div className="absolute inset-0" style={{
                        backgroundImage: `radial-gradient(#CBD5E1 1px, transparent 1px)`,
                        backgroundSize: '24px 24px',
                        opacity: 0.5
                    }}></div>
                    <div className="text-center z-10 p-8 bg-white/80 backdrop-blur rounded-2xl shadow-xl border border-white mt-10">
                        <div className="w-16 h-16 bg-[#0353A4]/10 rounded-full flex items-center justify-center mx-auto mb-4">
                            <MapIcon className="w-8 h-8 text-[#0353A4]" />
                        </div>
                        <h3 className="text-xl font-bold text-slate-800 mb-2">디지털 트윈 Map (추후 구현)</h3>
                        <p className="text-slate-500 text-sm">자율주행 모빌리티의 실시간 위치와 상태를 3D/2D 맵으로 렌더링 할 영역입니다.</p>
                    </div>
                </div>
            </div>

            {/* 우측: 사이드 패널 (E-Stop + 차량 리스트 + 카메라) */}
            <div className="w-[400px] flex flex-col gap-4 shrink-0">
                {/* 1. 상단: E-Stop 버튼 (최소한의 높이 h-12 고정, 너비 가득 참) */}
                <button
                    onClick={handleEStop}
                    className="w-full h-12 shrink-0 bg-red-600 hover:bg-red-700 text-white text-[15px] font-bold rounded-xl shadow-[0_4px_14px_0_rgba(220,38,38,0.39)] flex items-center justify-center gap-2 transition-transform active:scale-[0.98]"
                >
                    <AlertOctagon className="w-5 h-5 animate-pulse" />
                    EMERGENCY STOP (긴급 정지)
                </button>

                {/* 2. 중단: 차량 리스트 */}
                <div className="flex-[3] bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden flex flex-col">
                    <div className="h-12 border-b border-slate-100 flex items-center px-4 bg-slate-50/50 shrink-0">
                        <h3 className="font-bold text-slate-800 text-[15px] flex items-center gap-2">
                            <Truck className="w-4 h-4 text-[#0353A4]" /> 운영 차량 리스트
                        </h3>
                        <span className="ml-auto bg-[#0353A4] text-white px-2 py-0.5 rounded-full text-xs font-bold">
                            {vehicles.length}대
                        </span>
                    </div>
                    <div className="flex-1 overflow-y-auto p-3 space-y-2 custom-scrollbar">
                        {vehicles.map(v => (
                            <div
                                key={v.id}
                                onClick={() => setSelectedVehicleId(v.id)}
                                className={`p-3 rounded-lg border cursor-pointer transition-all ${selectedVehicleId === v.id
                                    ? 'border-[#0353A4] bg-[#F0F7FF] shadow-sm'
                                    : 'border-slate-200 hover:border-[#006DAA]/30 hover:bg-slate-50'
                                    }`}
                            >
                                <div className="flex items-center justify-between mb-2">
                                    <div className="font-bold text-slate-800 text-[15px]">{v.id}</div>
                                    <div className={`text-xs px-2 py-0.5 rounded-md border font-bold ${getStatusBadge(v.status)}`}>
                                        {v.status}
                                    </div>
                                </div>
                                <div className="space-y-1.5 text-xs text-slate-600 font-medium">
                                    <div className="flex items-center gap-2">
                                        <Navigation className="w-3.5 h-3.5 text-slate-400" />
                                        <span className="font-mono">{v.location.lat.toFixed(4)}, {v.location.lng.toFixed(4)}</span>
                                    </div>
                                    <div className="flex items-center justify-between mt-2 pt-2 border-t border-slate-200/50">
                                        <span className="flex items-center gap-1.5">
                                            배터리 <span className="font-bold text-slate-800">{v.battery}%</span>
                                        </span>
                                        <span className="flex items-center gap-1.5">
                                            속도 <span className="font-bold text-slate-800">{v.speed} km/h</span>
                                        </span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* 하단: 선택 차량 카메라 화면 */}
                <div className="flex-[2] bg-slate-900 rounded-xl shadow-sm border border-slate-800 overflow-hidden relative flex flex-col">
                    <div className="absolute top-3 left-3 z-10 bg-black/50 backdrop-blur-sm px-3 py-1.5 rounded text-white text-xs font-bold flex items-center gap-2 border border-white/10">
                        <Video className="w-3.5 h-3.5 text-red-400" />
                        {selectedVehicleId ? `${selectedVehicleId} 카메라` : '차량을 선택하세요'}
                        <span className="ml-1 w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse"></span>
                    </div>

                    {/* 실시간 카메라 영상 스트리밍 영역 목업 */}
                    <div className="flex-1 flex items-center justify-center relative overflow-hidden">
                        {false && (
                            selectedVehicleId ? (
                                <>
                                    {/* 카메라 목업 배경 */}
                                    <div className="absolute inset-0 bg-[#1a1c23]">
                                        {/* HUD 라인 */}
                                        <div className="absolute inset-0 opacity-20" style={{
                                            backgroundImage: `linear-gradient(rgba(255, 255, 255, 0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.1) 1px, transparent 1px)`,
                                            backgroundSize: '40px 40px'
                                        }}></div>
                                        <div className="absolute top-1/2 left-1/4 right-1/4 h-px bg-green-500/30"></div>
                                        <div className="absolute left-1/2 top-1/4 bottom-1/4 w-px bg-green-500/30"></div>
                                    </div>
                                    <div className="relative z-10 text-center">
                                        <Video className="w-10 h-10 text-slate-500 mx-auto mb-2 opacity-50" />
                                        <p className="text-slate-400 text-sm font-medium">실시간 주행 카메라 (추후 연동)</p>
                                    </div>
                                </>
                            ) : (
                                <p className="text-slate-500 text-sm font-medium">리스트에서 차량을 선택해주세요.</p>
                            )
                        )}

                        {/* 무조건 즉시 띄우는 스트리밍 영상 영역 (HTML 플레이어 지원을 위해 iframe 적용) */}
                        <div className="absolute inset-0 z-20 w-full h-full bg-black">
                            <iframe
                                src="/unity_cam/"
                                title="Camera stream"
                                className="w-full h-full border-none"
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
