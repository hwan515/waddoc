import { useNavigate } from 'react-router-dom';

const Home = () => {
    const navigate = useNavigate();
    
    // 임시 하드코딩된 이름. 추후 API 연동 시 상태로 관리될 예정
    const patientName = '홍길동';

    const handleStart = () => {
        navigate('/robot/setup');
    };

    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-[#061A40] font-sans relative overflow-hidden">
            {/* Background Decorations */}
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-[#0353A4] rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-[#B9D6F2] rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <div className="relative z-10 w-full max-w-4xl text-center space-y-4 mb-16 animate-fade-in-up">
                <h1 className="text-4xl md:text-5xl font-medium text-white tracking-tight leading-tight">
                    <span className="text-[#B9D6F2] font-bold">'{patientName}'</span>님 안녕하세요.<br/>
                    아래 버튼을 눌러 진료를 시작할 수 있습니다.
                </h1>
            </div>

            <button
                onClick={handleStart}
                className="relative z-10 w-full max-w-3xl py-12 md:py-16 bg-[#0353A4] hover:bg-[#006DAA] text-white text-3xl md:text-4xl font-semibold border border-[#006DAA] rounded-2xl shadow-xl shadow-[#0353A4]/30 transform hover:-translate-y-1 transition-all flex items-center justify-center animate-fade-in"
            >
                진료 시작하기
            </button>

            <style dangerouslySetInnerHTML={{
                __html: `
                @keyframes fadeInUp {
                    from { opacity: 0; transform: translateY(20px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .animate-fade-in-up { animation: fadeInUp 0.8s ease-out forwards; }
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                .animate-fade-in { animation: fadeIn 1s ease-out forwards; animation-delay: 0.3s; opacity: 0; }
            `}} />
        </div>
    );
};

export default Home;
