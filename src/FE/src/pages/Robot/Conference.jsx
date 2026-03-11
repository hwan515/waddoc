import { useEffect, useState } from 'react';
import { useWebRTC } from '../../hooks/useWebRTC';

const Conference = () => {
    // 환자는 방에 접속하는 Participant 역할 (isInitiator = false)
    // 현재 테스트를 위해 의사와 동일한 'test-room'을 하드코딩합니다. 나중에는 URL 파라미터나 상태값으로 받아와야 합니다.
    const {
        localVideoRef,
        remoteVideoRef,
        localStream,
        remoteStream, // 추가: 상대방 스트림
        initCamera,
        joinRoom,
        cleanupMedia
    } = useWebRTC('test-room', false);

    const [isJoined, setIsJoined] = useState(false);

    // 시작 시 바로 방에 입장 및 언마운트 시 정리
    useEffect(() => {
        const setupRoom = async () => {
            const stream = await initCamera();
            if (stream) {
                joinRoom(stream); // 강제로 얻어낸 스트림을 바로 주입
                setIsJoined(true);
            }
        };

        setupRoom();

        return () => {
            cleanupMedia();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // 내 카메라(local) 비디오 태그 연결 보장
    useEffect(() => {
        if (localVideoRef && localVideoRef.current && localStream) {
            if (localVideoRef.current.srcObject !== localStream) {
                localVideoRef.current.srcObject = localStream;
            }
        }
    }, [localStream, localVideoRef]);

    // 상대방 카메라(remote) 비디오 태그 연결 보장
    useEffect(() => {
        if (remoteVideoRef && remoteVideoRef.current && remoteStream) {
            if (remoteVideoRef.current.srcObject !== remoteStream) {
                remoteVideoRef.current.srcObject = remoteStream;
            }
        }
    }, [remoteStream, remoteVideoRef]);

    // 환자 로봇 화상 진료실 - 심플한 상대방 뷰 + 우측 하단 내 뷰
    return (
        <div className="w-screen h-screen bg-slate-900 relative overflow-hidden">
            {/* 메인 비디오 (의사 화면 - 전체 화면) */}
            <video
                ref={remoteVideoRef}
                autoPlay
                playsInline
                className="w-full h-full object-cover"
            />

            {/* 컴포넌트 마운트 전/초기 텅 빈 상태 시 보여줄 백그라운드 텍스트 (피드가 들어오면 가려짐) */}
            <div className="absolute inset-0 flex items-center justify-center -z-10 pointer-events-none">
                <span className="text-white/30 text-3xl font-bold tracking-widest">
                    {isJoined ? '상대방 화면 대기 중...' : '연결 중...'}
                </span>
            </div>

            {/* 내 비디오 (PIP, 우측 하단) */}
            <div className="absolute bottom-0 right-0 w-[480px] h-[360px] bg-slate-800 border-l border-t border-slate-700 shadow-2xl overflow-hidden">
                <video
                    ref={localVideoRef}
                    autoPlay
                    playsInline
                    muted
                    style={{ transform: 'scaleX(-1)' }}
                    className="w-full h-full object-cover"
                />
                {/* 피드가 없을 때 보여줄 텍스트 */}
                <div className="absolute inset-0 flex items-center justify-center -z-10 bg-[#FFF2CC] pointer-events-none">
                    <span className="text-black/50 text-2xl font-bold tracking-widest">내 화면</span>
                </div>
            </div>
        </div>
    );
};

export default Conference;
