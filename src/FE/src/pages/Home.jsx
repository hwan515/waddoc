import { Link } from 'react-router-dom';
import { Truck, Video, HeartPulse, MapPin } from 'lucide-react';

// eslint-disable-next-line no-unused-vars
const FeatureCard = ({ icon: Icon, title, description, delay }) => (
    <div
        className="bg-white p-6 rounded-2xl shadow-xl shadow-slate-200/50 border border-slate-100/50 hover:-translate-y-1 hover:shadow-2xl hover:shadow-primary/20 transition-all duration-300 group"
        style={{ animation: `fadeInUp 0.6s ease-out ${delay}s both` }}
    >
        <div className="bg-primary/5 w-14 h-14 rounded-xl flex items-center justify-center mb-6 group-hover:bg-primary/10 transition-colors">
            <Icon className="w-7 h-7 text-primary" />
        </div>
        <h3 className="text-xl font-bold text-slate-800 mb-3">{title}</h3>
        <p className="text-slate-600 leading-relaxed text-sm">
            {description}
        </p>
    </div>
);

const Home = () => {
    return (
        <div className="w-full flex flex-col">
            {/* Hero Section */}
            <section className="relative w-full overflow-hidden bg-gradient-to-br from-dark via-primary to-accent-1 py-32 lg:py-48 flex items-center justify-center">
                {/* Abstract Background Shapes */}
                <div className="absolute top-0 left-0 w-full h-full overflow-hidden z-0">
                    <div className="absolute -top-20 -left-20 w-96 h-96 bg-accent-1 rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob"></div>
                    <div className="absolute top-40 -right-20 w-80 h-80 bg-secondary rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob animation-delay-2000"></div>
                    <div className="absolute -bottom-40 left-1/2 w-96 h-96 bg-primary rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob animation-delay-4000"></div>
                </div>

                <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center text-white">
                    <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight mb-6">
                        모든 곳을 연결하는 <br className="hidden sm:block" />
                        <span className="text-transparent bg-clip-text bg-gradient-to-r from-secondary to-white">
                            자율주행 원격 의료 시스템
                        </span>
                    </h1>
                    <p className="mt-4 max-w-2xl mx-auto text-lg sm:text-xl text-secondary/90 mb-10">
                        앱 설치 없이 전화 한 통으로 예약하고, 자율주행 로봇이 집 앞까지 찾아가는
                        도서·산간 지역 맞춤형 비대면 진료 플랫폼입니다.
                    </p>
                    <div className="flex flex-col sm:flex-row gap-4 justify-center items-center">
                        <Link
                            to="/signup"
                            className="px-8 py-3.5 border border-transparent text-base font-medium rounded-full text-primary bg-white hover:bg-slate-50 hover:shadow-lg hover:shadow-white/20 transition-all focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-primary focus:ring-white"
                        >
                            지금 시작하기
                        </Link>
                        <Link
                            to="/login"
                            className="px-8 py-3.5 border border-white/30 text-base font-medium rounded-full text-white bg-white/10 backdrop-blur-sm hover:bg-white/20 transition-all focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-primary focus:ring-white"
                        >
                            로그인
                        </Link>
                    </div>
                </div>
            </section>

            {/* Features Section */}
            <section className="py-24 bg-slate-50 relative">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="text-center mb-16">
                        <h2 className="text-sm font-bold text-accent-1 uppercase tracking-wider mb-2">
                            Features
                        </h2>
                        <h3 className="text-3xl font-extrabold text-slate-900 sm:text-4xl">
                            MVP 주요 기능
                        </h3>
                        <p className="mt-4 max-w-2xl text-lg text-slate-500 mx-auto">
                            환자, 의사, 보호자, 운영자 모두를 위한 통합 설루션을 제공합니다.
                        </p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
                        <FeatureCard
                            icon={Truck}
                            title="MORAI 자율주행 출동"
                            description="환자 집 앞까지 정확히 도착하는 지정 경로 인지 자율주행 로봇 이동 서비스를 지원합니다."
                            delay={0.1}
                        />
                        <FeatureCard
                            icon={Video}
                            title="WebRTC 화상 진료"
                            description="탑승 시 별도 앱 없이 즉시 화상 진료 세션이 개설되어 안정적인 비대면 진료를 수행합니다."
                            delay={0.2}
                        />
                        <FeatureCard
                            icon={HeartPulse}
                            title="Vital 생체 측정"
                            description="탑승과 동시에 환자 본인 확인 및 동의를 거쳐 중요 활력징후(혈압, 체온, 맥박 등)를 측정합니다."
                            delay={0.3}
                        />
                        <FeatureCard
                            icon={MapPin}
                            title="실시간 관제 시스템"
                            description="보호자 및 운영자가 차량의 상태와 환자의 진료 진행 상황(ETA)을 실시간으로 함께 모니터링합니다."
                            delay={0.4}
                        />
                    </div>
                </div>
            </section>

            {/* Global CSS for animations (can be moved to index.css later) */}
            <style dangerouslySetInnerHTML={{
                __html: `
        @keyframes blob {
          0% { transform: translate(0px, 0px) scale(1); }
          33% { transform: translate(30px, -50px) scale(1.1); }
          66% { transform: translate(-20px, 20px) scale(0.9); }
          100% { transform: translate(0px, 0px) scale(1); }
        }
        .animate-blob {
          animation: blob 7s infinite;
        }
        .animation-delay-2000 {
          animation-delay: 2s;
        }
        .animation-delay-4000 {
          animation-delay: 4s;
        }
        @keyframes fadeInUp {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}} />
        </div>
    );
};

export default Home;
