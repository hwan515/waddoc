import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { Home, LogIn, UserPlus, LogOut, Activity } from 'lucide-react';
import useAuthStore from '../../store/authStore';

const MainLayout = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuthStore();

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    const isActive = (path) => location.pathname === path;

    return (
        <div className="min-h-screen flex flex-col bg-slate-50 font-sans">
            {/* Header */}
            <header className="sticky top-0 z-50 w-full backdrop-blur-md bg-white/80 border-b border-slate-200/50 shadow-sm">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex justify-between items-center h-16">
                        {/* Logo */}
                        <Link to="/" className="flex items-center gap-2 group">
                            <div className="bg-primary/10 p-2 rounded-lg group-hover:bg-primary/20 transition-colors">
                                <Activity className="w-6 h-6 text-primary" strokeWidth={2.5} />
                            </div>
                            <span className="font-bold text-xl text-dark tracking-tight">
                                Vital<span className="text-primary">Connect</span>
                            </span>
                        </Link>

                        {/* Navigation / Auth */}
                        <div className="flex items-center gap-4">
                            {user ? (
                                <>
                                    <div className="hidden sm:flex items-center text-sm font-medium text-slate-600 mr-4">
                                        <span className="bg-secondary/30 text-primary px-2.5 py-1 rounded-full text-xs mr-2">
                                            {user.role}
                                        </span>
                                        {user.name}님 환영합니다
                                    </div>
                                    <button
                                        onClick={handleLogout}
                                        className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-slate-600 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                                    >
                                        <LogOut className="w-4 h-4" />
                                        로그아웃
                                    </button>
                                </>
                            ) : (
                                <>
                                    <Link
                                        to="/login"
                                        className={`flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors ${isActive('/login')
                                                ? 'bg-primary/10 text-primary'
                                                : 'text-slate-600 hover:bg-slate-100'
                                            }`}
                                    >
                                        <LogIn className="w-4 h-4" />
                                        로그인
                                    </Link>
                                    <Link
                                        to="/signup"
                                        className={`flex items-center gap-2 px-4 py-2 text-sm font-medium text-white rounded-lg transition-all shadow-md hover:shadow-lg ${isActive('/signup')
                                                ? 'bg-accent-1 shadow-accent-1/20'
                                                : 'bg-primary hover:bg-accent-1 shadow-primary/20'
                                            }`}
                                    >
                                        <UserPlus className="w-4 h-4" />
                                        회원가입
                                    </Link>
                                </>
                            )}
                        </div>
                    </div>
                </div>
            </header>

            {/* Main Content */}
            <main className="flex-grow flex flex-col relative w-full h-full">
                <Outlet />
            </main>

            {/* Footer */}
            <footer className="bg-white border-t border-slate-200 mt-auto py-8">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row justify-between items-center gap-4">
                    <p className="text-slate-500 text-sm">
                        &copy; 2026 VitalConnect. 도서·산간 원격 의료 지원 서비스.
                    </p>
                    <div className="flex gap-4 text-sm text-slate-400">
                        <a href="#" className="hover:text-primary transition-colors">이용약관</a>
                        <a href="#" className="hover:text-primary transition-colors">개인정보처리방침</a>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default MainLayout;
