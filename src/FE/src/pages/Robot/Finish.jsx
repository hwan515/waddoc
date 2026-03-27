import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const FINISH_REDIRECT_DELAY_MS = 10000;

const clearRobotSessionState = () => {
    localStorage.removeItem('robot_mission_id');
    localStorage.removeItem('robot_session_id');
    localStorage.removeItem('webrtc_terminal_token');
    localStorage.removeItem('robot_device_terminal_token');
    localStorage.removeItem('robot_mission_terminal_token');
    localStorage.removeItem('current_mission_id');
    localStorage.removeItem('current_patient_name');
};

const Finish = () => {
    const navigate = useNavigate();

    useEffect(() => {
        clearRobotSessionState();

        const timerId = setTimeout(() => {
            navigate('/robot', { replace: true });
        }, FINISH_REDIRECT_DELAY_MS);

        return () => {
            clearTimeout(timerId);
        };
    }, [navigate]);

    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-[#061A40] font-sans relative overflow-hidden">
            {/* Background Decorations */}
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-[#0353A4] rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-[#B9D6F2] rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <div className="relative z-10 w-full max-w-4xl text-center space-y-10 mb-16 animate-fade-in-up">
                <h1 className="text-4xl md:text-6xl font-bold text-white tracking-tight leading-tight mb-8">
                    진료가 종료되었습니다.
                </h1>

                <div className="text-xl md:text-3xl text-[#B9D6F2] font-medium leading-relaxed space-y-2">
                    <p className="text-white font-bold">감사합니다.</p>
                    <p>천천히 차량에서 하차해주세요.</p>
                    <p className="pt-4">진료를 통해 처방 받으신 약은</p>
                    <p>
                        <span className="text-white font-bold">금일 오후 8~10시</span> 사이에 배송 예정입니다.
                    </p>
                    <p className="pt-6 text-base md:text-xl text-slate-300">
                        잠시 후 차량이 자율 주행중입니다. 화면으로 전환됩니다.
                    </p>
                </div>
            </div>
        </div>
    );
};

export default Finish;
