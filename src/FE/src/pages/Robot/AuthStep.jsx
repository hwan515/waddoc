import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as faceapi from 'face-api.js';
import { ScanFace, CheckCircle2, AlertCircle } from 'lucide-react';

const AuthStep = () => {
    const videoRef = useRef(null);
    const navigate = useNavigate();

    const [isModelLoaded, setIsModelLoaded] = useState(false);
    const [countdown, setCountdown] = useState(null);
    const [authStatus, setAuthStatus] = useState('idle'); // idle, capturing, success, fail
    const [errorMsg, setErrorMsg] = useState('');
    const [failCount, setFailCount] = useState(0);
    const failCountRef = useRef(0);

    const authStatusRef = useRef('idle'); // To access latest status in setInterval
    useEffect(() => {
        authStatusRef.current = authStatus;
    }, [authStatus]);

    const detectionIntervalRef = useRef(null);
    const countdownRef = useRef(null);

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

    const handleVideoPlay = () => {
        if (!isModelLoaded) return;

        // Start detecting face every 200ms
        detectionIntervalRef.current = setInterval(async () => {
            const currentStatus = authStatusRef.current;
            if (currentStatus === 'capturing' || currentStatus === 'success' || currentStatus === 'fail') {
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
            // 1. Canvas에 현재 비디오 프레임 그리기
            const canvas = document.createElement('canvas');
            canvas.width = videoRef.current.videoWidth;
            canvas.height = videoRef.current.videoHeight;
            const ctx = canvas.getContext('2d');

            ctx.translate(canvas.width, 0);
            ctx.scale(-1, 1);
            ctx.drawImage(videoRef.current, 0, 0);

            // 2. Base64 이미지 추출
            const base64Image = canvas.toDataURL('image/jpeg', 0.9);

            // 3. 로컬 테스트를 위해 FE 폴더 경로에 캡처 이미지 직접 저장 요청 (vite plugin 이용)
            const currentId = parseInt(localStorage.getItem('auth_image_id') || '0', 10) + 1;
            localStorage.setItem('auth_image_id', currentId.toString());
            const fileName = `image_${currentId}.jpg`;

            try {
                await fetch('/api/local-save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ image: base64Image, filename: fileName })
                });
            } catch (localErr) {
                console.warn('Local save failed. Make sure vite plugin is running.', localErr);
            }

            // 4. 외부 AI 서버로 본인인증 요청 
            const response = await fetch('http://localhost:5000/verify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ img: base64Image })
            });
            const result = await response.json();

            // 5. 서버 검증 결과 판별
            if (result.verified) {
                setAuthStatus('success');
                setFailCount(0); // 성공시 초기화
                failCountRef.current = 0;
                stopVideo();
                setTimeout(() => {
                    navigate('/robot/measure-intro');
                }, 2000);
            } else {
                handleAuthFail();
            }

        } catch (err) {
            console.error("Auth Error:", err);
            handleAuthFail();
        }
    };

    const handleAuthFail = () => {
        setAuthStatus('fail');
        failCountRef.current += 1;
        setFailCount(failCountRef.current);

        if (failCountRef.current >= 5) {
            setErrorMsg("진료 예약했던 번호로 문의해주세요.");
            // 5회 이상 실패 시 무한 루프를 막거나 완전히 종료하려면 여기서 리셋 타이머를 안 줄 수도 있습니다.
            // 일단은 에러 유지 상태로 둠
        } else {
            setErrorMsg("인증에 실패하여 다시 시도해주세요");
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
                    <p className="text-xl text-slate-300 font-medium">
                        본인인증을 위해 얼굴을 흰색 선에 맞춰주세요.
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
                        </svg>
                    </div>

                    {/* Countdown UI */}
                    {countdown !== null && authStatus === 'idle' && (
                        <div className="absolute inset-0 z-20 flex items-center justify-center bg-[#061A40]/30 backdrop-blur-sm animate-fade-in">
                            <div className="text-8xl font-black text-white drop-shadow-[0_0_20px_rgba(50,215,75,0.8)] animate-bounce-custom">
                                {countdown}
                            </div>
                        </div>
                    )}

                    {/* Capturing / Success States */}
                    {authStatus === 'capturing' && (
                        <div className="absolute inset-0 z-30 flex items-center justify-center bg-white/90 animate-flash">
                            <span className="text-3xl font-bold text-[#061A40]">얼굴 촬영 중...</span>
                        </div>
                    )}

                    {authStatus === 'success' && (
                        <div className="absolute inset-0 z-30 flex flex-col items-center justify-center bg-[#061A40]/90 backdrop-blur-md animate-fade-in">
                            <CheckCircle2 className="w-24 h-24 text-green-400 mb-4 animate-scale-up" />
                            <h2 className="text-3xl font-bold text-white mb-2">본인 인증 완료</h2>
                            <p className="text-[#B9D6F2] text-lg font-medium">화상 진료실로 이동합니다...</p>
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
