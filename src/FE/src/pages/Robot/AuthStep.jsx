import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as faceapi from 'face-api.js';
import { ScanFace, CheckCircle2, AlertCircle } from 'lucide-react';

import apiClient from '../../utils/api'; 

const AuthStep = () => {
    const videoRef = useRef(null);
    const navigate = useNavigate();

    const [isModelLoaded, setIsModelLoaded] = useState(false);
    const [countdown, setCountdown] = useState(null);
    const [authStatus, setAuthStatus] = useState('idle'); // idle, capturing, submitting, success, fail
    const [captureStep, setCaptureStep] = useState('face'); // 'face' -> 'idcard' -> 'submitting' -> 'done'
    const [errorMsg, setErrorMsg] = useState('');
    const failCountRef = useRef(0);

    const [faceImgData, setFaceImgData] = useState(null);

    const authStatusRef = useRef('idle');
    const captureStepRef = useRef('face');

    useEffect(() => {
        authStatusRef.current = authStatus;
    }, [authStatus]);
    
    useEffect(() => {
        captureStepRef.current = captureStep;
    }, [captureStep]);

    const detectionIntervalRef = useRef(null);
    const countdownRef = useRef(null);

    const startVideo = () => {
        navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 } })
            .then((stream) => {
                if (videoRef.current) {
                    videoRef.current.srcObject = stream;
                }
            })
            .catch((err) => {
                console.error(err);
                setErrorMsg("카메라 접근을 허용해주세요.");
            });
    };

    const stopVideo = () => {
        if (videoRef.current && videoRef.current.srcObject) {
            videoRef.current.srcObject.getTracks().forEach(track => track.stop());
        }
    };

    const extractApiErrorMessage = (error, fallbackMessage) => {
        const detail = error?.response?.data?.detail;
        const message = error?.response?.data?.message;
        return detail || message || fallbackMessage;
    };

    // base64를 Blob으로 변환하는 유틸 함수
    const base64ToBlob = (base64, mimeType = 'image/jpeg') => {
        const resolvedMimeType = mimeType || base64.match(/^data:([^;]+);base64,/)?.[1] || 'image/jpeg';
        const byteString = atob(base64.split(',')[1]);
        const ab = new ArrayBuffer(byteString.length);
        const ia = new Uint8Array(ab);
        for (let i = 0; i < byteString.length; i++) {
            ia[i] = byteString.charCodeAt(i);
        }
        return new Blob([ab], { type: resolvedMimeType });
    };

    const captureVideoFrame = ({ cropRect } = {}) => {
        const video = videoRef.current;
        if (!video) {
            throw new Error('Video stream is not ready');
        }

        const sourceWidth = video.videoWidth;
        const sourceHeight = video.videoHeight;
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');

        if (!ctx) {
            throw new Error('Canvas context is not available');
        }

        if (!cropRect) {
            canvas.width = sourceWidth;
            canvas.height = sourceHeight;
            ctx.drawImage(video, 0, 0, sourceWidth, sourceHeight);
            return canvas;
        }

        const containerWidth = video.clientWidth || sourceWidth;
        const containerHeight = video.clientHeight || sourceHeight;
        const scale = Math.max(containerWidth / sourceWidth, containerHeight / sourceHeight);
        const renderedWidth = sourceWidth * scale;
        const renderedHeight = sourceHeight * scale;
        const offsetX = Math.max(0, (renderedWidth - containerWidth) / 2);
        const offsetY = Math.max(0, (renderedHeight - containerHeight) / 2);

        const overlayX = containerWidth * cropRect.x;
        const overlayY = containerHeight * cropRect.y;
        const overlayWidth = containerWidth * cropRect.width;
        const overlayHeight = containerHeight * cropRect.height;

        const sourceX = Math.max(0, Math.round((overlayX + offsetX) / scale));
        const sourceY = Math.max(0, Math.round((overlayY + offsetY) / scale));
        const sourceCropWidth = Math.min(sourceWidth - sourceX, Math.round(overlayWidth / scale));
        const sourceCropHeight = Math.min(sourceHeight - sourceY, Math.round(overlayHeight / scale));

        canvas.width = Math.max(1, sourceCropWidth);
        canvas.height = Math.max(1, sourceCropHeight);
        ctx.drawImage(
            video,
            sourceX,
            sourceY,
            sourceCropWidth,
            sourceCropHeight,
            0,
            0,
            canvas.width,
            canvas.height
        );
        return canvas;
    };

    // Load face-api models on mount
    useEffect(() => {
        const loadModels = async () => {
            try {
                // public/models 폴더 하위에 모델 파일이 있어야 합니다.
                await Promise.all([
                    faceapi.nets.tinyFaceDetector.loadFromUri('/models'),
                    faceapi.nets.faceLandmark68Net.loadFromUri('/models')
                ]);
                setIsModelLoaded(true);
                startVideo();
            } catch (err) {
                console.error("Failed to load models:", err);
                setErrorMsg("얼굴 인식 모델을 불러오는데 실패했습니다.");
            }
        };
        loadModels();

        return () => {
            stopVideo();
            if (detectionIntervalRef.current) clearInterval(detectionIntervalRef.current);
            if (countdownRef.current) clearInterval(countdownRef.current);
        };
    }, []);

    const handleVideoPlay = () => {
        if (!isModelLoaded) return;

        // Start detecting face every 200ms
        detectionIntervalRef.current = setInterval(async () => {
            const currentStatus = authStatusRef.current;
            const currentStep = captureStepRef.current;
            
            // 얼굴 캡처 단계가 아니거나 통신 중이면 감지 중단
            if (currentStep !== 'face' || currentStatus === 'capturing' || currentStatus === 'submitting' || currentStatus === 'success' || currentStatus === 'fail') {
                resetCountdown();
                return;
            }
            if (!videoRef.current) return;

            const detections = await faceapi.detectSingleFace(
                videoRef.current,
                new faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.5 })
            ).withFaceLandmarks();

            if (detections) {
                const video = videoRef.current;
                const box = detections.detection.box;

                // 간단한 정렬 판별 로직 (화면 중앙 & 적정 크기)
                const vw = video.videoWidth;
                const vh = video.videoHeight;

                const centerX = vw / 2;
                const centerY = vh / 2;

                // 인식된 얼굴의 중심값
                const faceCenterX = box.x + box.width / 2;
                const faceCenterY = box.y + box.height / 2;

                // 중앙에서 벗어난 허용 오차 (픽셀)
                const isAlignedX = Math.abs(centerX - faceCenterX) < (vw * 0.15); // 화면 너비의 15% 이내
                const isAlignedY = Math.abs(centerY - faceCenterY) < (vh * 0.15); // 화면 높이의 15% 이내

                // 얼굴 크기가 화면에서 차지하는 비율 점검 (가이드라인에 맞도록)
                const isGoodSize = box.width > (vw * 0.2) && box.width < (vw * 0.45);

                if (isAlignedX && isAlignedY && isGoodSize) {
                    if (countdownRef.current === null) {
                        startCountdown();
                    }
                } else {
                    resetCountdown();
                }
            } else {
                resetCountdown();
            }
        }, 200);
    };

    const startCountdown = () => {
        setCountdown(3);
        let count = 3;

        countdownRef.current = setInterval(() => {
            count -= 1;
            setCountdown(count);

            if (count === 0) {
                resetCountdown(); // Clear interval
                captureFace();
            }
        }, 1000);
    };

    const resetCountdown = () => {
        if (countdownRef.current) {
            clearInterval(countdownRef.current);
            countdownRef.current = null;
        }
        setCountdown(null);
    };

    const captureFace = async () => {
        setAuthStatus('capturing');

        try {
            const canvas = captureVideoFrame();
            const base64Image = canvas.toDataURL('image/jpeg', 0.95);
            setFaceImgData(base64Image); // 얼굴 이미지 상태 저장

            // 3. 신분증 촬영 단계로 넘어감
            setTimeout(() => {
                setCaptureStep('idcard');
                setAuthStatus('idle');
            }, 500);

        } catch (err) {
            console.error("Face Capture Error:", err);
            handleAuthFail();
        }
    };

    const captureIdCard = async () => {
        setAuthStatus('capturing');
        try {
            const canvas = captureVideoFrame({
                cropRect: { x: 0.25, y: 0.30, width: 0.50, height: 0.40 }
            });
            const idCardBase64 = canvas.toDataURL('image/png');
            
            setCaptureStep('submitting');
            await submitAuth(faceImgData, idCardBase64);

        } catch (err) {
            console.error("ID Card Capture Error:", err);
            handleAuthFail();
        }
    };

    const submitAuth = async (faceBase64, idCardBase64) => {
        setAuthStatus('submitting');
        try {
            const missionId = localStorage.getItem('current_mission_id');
            const terminalToken = localStorage.getItem('robot_mission_terminal_token');
            console.log("🚀 [인증 시작] 대상 미션 ID:", missionId);

            if (!missionId) {
                handleAuthFail('선택된 미션이 없습니다. 진료 시작 화면으로 돌아가 다시 진행해주세요.');
                return;
            }

            if (!terminalToken) {
                handleAuthFail('차량 단말 인증 정보가 없습니다. 진료 시작 화면으로 돌아가 다시 진행해주세요.');
                return;
            }
            
            const formData = new FormData();
            formData.append('faceImage', base64ToBlob(faceBase64), 'face.jpg');
            formData.append('idCardImage', base64ToBlob(idCardBase64), 'idcard.png');

            await apiClient.post(`/missions/${missionId}/identity-check`, formData, {
                headers: { 
                    'Content-Type': 'multipart/form-data',
                    'Authorization': `Bearer ${terminalToken}`
                } 
            });
            
            setAuthStatus('success');
            failCountRef.current = 0;
            stopVideo();
            setTimeout(() => {
                navigate('/robot/measure-intro');
            }, 2000);
        } catch (err) {
            console.error("Auth Error:", err);
            handleAuthFail(extractApiErrorMessage(err, '본인 인증에 실패했습니다. 다시 시도해주세요.'));
        }
    };

    const handleAuthFail = (message = '인증에 실패하여 다시 시도해주세요') => {
        setAuthStatus('fail');
        failCountRef.current += 1;
        setCaptureStep('face');
        setFaceImgData(null);

        if (failCountRef.current >= 5) {
            setErrorMsg("진료 예약했던 번호로 문의해주세요.");
            // 5회 이상 실패 시 무한 루프를 막거나 완전히 종료하려면 여기서 리셋 타이머를 안 줄 수도 있습니다.
            // 일단은 에러 유지 상태로 둠
        } else {
            setErrorMsg(message);
            setTimeout(() => {
                setAuthStatus('idle');
                setErrorMsg('');
            }, 3000);
        }
    };
    return (
        <div className="min-h-screen bg-[#061A40] flex flex-col items-center justify-center relative overflow-hidden text-white font-sans">
            {/* Background Decorations */}
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-[#0353A4] rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-[#B9D6F2] rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <main className="z-10 flex flex-col items-center max-w-5xl w-full p-8">

                {/* Header Info */}
                <div className="text-center mb-8 animate-fade-in-up">
                    <h1 className="text-4xl font-extrabold text-[#B9D6F2] tracking-tight mb-3 flex items-center justify-center gap-3">
                        <ScanFace className="w-10 h-10" />
                        본인 인증
                    </h1>
                    <p className="text-xl text-slate-300 font-medium h-8">
                        {captureStep === 'face' 
                            ? '본인인증을 위해 얼굴을 흰색 선에 맞춰주세요.'
                            : captureStep === 'idcard'
                            ? '화면의 네모 영역에 신분증이 꽉 차도록 비춘 뒤 버튼을 눌러주세요.'
                            : captureStep === 'submitting'
                            ? '본인 확인을 검증하는 중입니다...'
                            : '인증이 완료되었습니다.'}
                    </p>
                    {errorMsg && (
                        <p className="mt-4 text-red-400 font-bold bg-red-400/10 px-4 py-2 rounded-xl flex items-center gap-2 justify-center">
                            <AlertCircle className="w-5 h-5" />
                            {errorMsg}
                        </p>
                    )}
                </div>

                {/* Camera View Box */}
                <div className="relative w-full max-w-3xl aspect-[16/10] bg-[#001D3D] rounded-3xl border border-white/20 shadow-2xl overflow-hidden backdrop-blur-md">

                    {!isModelLoaded ? (
                        <div className="absolute inset-0 flex flex-col items-center justify-center z-20 bg-[#061A40]/80">
                            <div className="w-12 h-12 border-4 border-[#0353A4] border-t-transparent rounded-full animate-spin mb-4"></div>
                            <span className="text-[#B9D6F2] font-bold text-lg animate-pulse">인식 모델을 불러오는 중입니다...</span>
                        </div>
                    ) : (
                        <video
                            ref={videoRef}
                            autoPlay
                            muted
                            playsInline
                            className="absolute inset-0 w-full h-full object-cover custom-video-mirror"
                            onPlay={handleVideoPlay}
                        />
                    )}

                    {/* Camera Overlay Guide */}
                    <div className="absolute inset-0 pointer-events-none z-10 flex items-center justify-center">
                        <svg className="w-full h-full" preserveAspectRatio="none">
                            {captureStep === 'face' ? (
                                <>
                                    {/* SVG Mask for highlighting the face area */}
                                    <defs>
                                        <mask id="face-mask">
                                            <rect width="100%" height="100%" fill="white" />
                                            <ellipse cx="50%" cy="50%" rx="18%" ry="35%" fill="black" />
                                        </mask>
                                    </defs>
                                    <rect width="100%" height="100%" fill="rgba(6, 26, 64, 0.6)" mask="url(#face-mask)" />
        
                                    {/* Guide Dashed Line */}
                                    <ellipse
                                        cx="50%"
                                        cy="50%"
                                        rx="18%"
                                        ry="35%"
                                        fill="none"
                                        stroke={countdown !== null ? "#32D74B" : "rgba(255, 255, 255, 0.5)"}
                                        strokeWidth="4"
                                        strokeDasharray={countdown !== null ? "none" : "10 10"}
                                        className="transition-colors duration-300"
                                    />
                                </>
                            ) : (
                                <>
                                    <defs>
                                        <mask id="idcard-mask">
                                            <rect width="100%" height="100%" fill="white" />
                                            <rect x="25%" y="30%" width="50%" height="40%" rx="15" fill="black" />
                                        </mask>
                                    </defs>
                                    <rect width="100%" height="100%" fill="rgba(6, 26, 64, 0.6)" mask="url(#idcard-mask)" />
                                    
                                    <rect 
                                        x="25%" y="30%" width="50%" height="40%" rx="15"
                                        fill="none"
                                        stroke="rgba(255, 255, 255, 0.8)"
                                        strokeWidth="4"
                                        strokeDasharray="15 15"
                                    />
                                </>
                            )}
                        </svg>
                    </div>

                    {/* ID Card Capture UI */}
                    {captureStep === 'idcard' && authStatus === 'idle' && (
                        <div className="absolute bottom-8 left-0 right-0 z-20 flex justify-center animate-fade-in-up">
                            <button 
                                onClick={captureIdCard}
                                className="bg-[#B9D6F2] hover:bg-white text-[#061A40] font-bold text-xl px-10 py-4 rounded-full shadow-[0_0_20px_rgba(185,214,242,0.4)] transition-all flex items-center gap-2"
                            >
                                <ScanFace className="w-6 h-6" />
                                신분증 촬영하기
                            </button>
                        </div>
                    )}

                    {/* Countdown UI */}
                    {countdown !== null && authStatus === 'idle' && (
                        <div className="absolute inset-0 z-20 flex items-center justify-center bg-[#061A40]/30 backdrop-blur-sm animate-fade-in">
                            <div className="text-8xl font-black text-white drop-shadow-[0_0_20px_rgba(50,215,75,0.8)] animate-bounce-custom">
                                {countdown}
                            </div>
                        </div>
                    )}

                    {/* Capturing / Submitting States */}
                    {(authStatus === 'capturing' || authStatus === 'submitting') && (
                        <div className="absolute inset-0 z-30 flex flex-col items-center justify-center bg-white/90 animate-flash">
                            <div className="w-16 h-16 border-4 border-[#0353A4] border-t-transparent rounded-full animate-spin mb-4"></div>
                            <span className="text-3xl font-bold text-[#061A40]">
                                {authStatus === 'capturing' ? '촬영 중...' : '신원 검증 중입니다...'}
                            </span>
                        </div>
                    )}

                    {authStatus === 'success' && (
                        <div className="absolute inset-0 z-30 flex flex-col items-center justify-center bg-[#061A40]/90 backdrop-blur-md animate-fade-in">
                            <CheckCircle2 className="w-24 h-24 text-green-400 mb-4 animate-scale-up" />
                            <h2 className="text-3xl font-bold text-white mb-2">본인 인증 완료</h2>
                            <p className="text-[#B9D6F2] text-lg font-medium">건강정보 측정 단계로 넘어갑니다...</p>
                        </div>
                    )}
                </div>

            </main>

            <style dangerouslySetInnerHTML={{
                __html: `
                .custom-video-mirror {
                    transform: scaleX(-1);
                }
                @keyframes bounce-custom {
                    0%, 100% { transform: translateY(-5%); }
                    50% { transform: translateY(5%); }
                }
                .animate-bounce-custom {
                    animation: bounce-custom 1s ease-in-out infinite;
                }
                @keyframes flash {
                    0% { opacity: 0; }
                    10% { opacity: 1; }
                    100% { opacity: 1; }
                }
                .animate-flash {
                    animation: flash 0.3s ease-out forwards;
                }
                @keyframes scaleUp {
                    0% { transform: scale(0.5); opacity: 0; }
                    100% { transform: scale(1); opacity: 1; }
                }
                .animate-scale-up {
                    animation: scaleUp 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
                }
                @keyframes fadeInUp {
                    from { opacity: 0; transform: translateY(20px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .animate-fade-in-up { animation: fadeInUp 0.7s ease-out forwards; }
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                .animate-fade-in { animation: fadeIn 0.3s ease-out forwards; }
            `}} />
        </div>
    );
};

export default AuthStep;
