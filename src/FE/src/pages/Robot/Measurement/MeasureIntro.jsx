import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MEASUREMENT_INTRO_DELAY_MS } from './measurementTiming';

const MeasureIntro = () => {
    const navigate = useNavigate();

    // 3초 후 다음 단계(체온 측정 화면)로 자동 이동
    useEffect(() => {
        const timer = setTimeout(() => {
            // 체온 측정 화면 라우트로 이동
            navigate('/robot/measure/temperature');
        }, MEASUREMENT_INTRO_DELAY_MS);
        return () => clearTimeout(timer);
    }, [navigate]);

    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-dark font-sans relative overflow-hidden text-center cursor-pointer" onClick={() => navigate('/robot/measure/temperature')}>
            {/* Background Decorations */}
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-primary rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-secondary rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <main className="relative z-10 w-full max-w-4xl space-y-12 animate-fade-in-up">

                <div className="space-y-3">
                    <h1 className="text-3xl md:text-4xl font-semibold text-secondary tracking-tight">
                        비대면진료 시작 전,
                    </h1>
                    <h2 className="text-3xl md:text-4xl font-semibold text-secondary tracking-tight">
                        환자분의 건강정보를 측정합니다.
                    </h2>
                </div>

                <div className="text-5xl md:text-3xl text-secondary font-medium tracking-wide">
                    총 <span className="text-white font-bold">4단계</span>로 <span className="text-white font-bold">체온, 혈압, 심전도, 산소포화도</span>를 측정합니다.
                </div>

                <div className="text-4xl md:text-3xl font-medium text-secondary tracking-wide pt-4">
                    가장 먼저 <span className="font-bold text-white">체온 측정</span>을 시작하겠습니다.
                </div>

            </main>

            {/* 화면을 클릭하면 스킵 가능하다는 작은 안내 문구 */}
            <div className="absolute bottom-10 text-secondary/50 text-sm animate-pulse">
                화면을 터치하면 바로 시작합니다
            </div>
        </div>
    );
};

export default MeasureIntro;
