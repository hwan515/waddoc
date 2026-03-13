import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import MainLayout from './components/layout/MainLayout';
import Login from './pages/Auth/Login';
import Signup from './pages/Auth/Signup';
import DoctorDashboard from './pages/Doctor/Dashboard';
import VideoConference from './pages/Doctor/VideoConference';
import ControlCenter from './pages/Operator/ControlCenter';
import AdminLogin from './pages/Auth/AdminLogin';
import AdminSignup from './pages/Auth/AdminSignup';
import PatientPortal from './pages/Patient/Portal';
import PatientMyPage from './pages/Patient/MyPage';
import AuthStep from './pages/Robot/AuthStep';
import Conference from './pages/Robot/Conference';

// Mock EMR
import EMRLogin from './pages/Doctor/EMRLogin';
import EMRDashboard from './pages/Doctor/EMR/Dashboard';

function App() {
  return (
    <Router>
      <Routes>
        {/* 메인 페이지가 바로 로그인 페이지가 되도록 설정 */}
        <Route path="/" element={<Login />} />
        <Route path="/signup" element={<Signup />} />

        {/* 관리자 전용 인증 페이지 */}
        <Route path="/admin/login" element={<AdminLogin />} />
        <Route path="/admin/signup" element={<AdminSignup />} />

        {/* 화상 진료 페이지 (사이드바 없이 전체화면) */}
        <Route path="/doctor/consultation/:id" element={<VideoConference />} />

        {/* 관제/운영 풀스크린 단독화면 */}
        <Route path="/operator/control" element={<ControlCenter />} />

        {/* 의사용 외부 EMR 모의 시스템 */}
        <Route path="/emr/login" element={<EMRLogin />} />
        <Route path="/emr/dashboard" element={<EMRDashboard />} />

        {/* 환자/보호자 전용 포털 화면 */}
        <Route path="/patient/portal" element={<PatientPortal />} />
        <Route path="/patient/mypage" element={<PatientMyPage />} />

        {/* 로봇 (환자) 전용 화면 */}
        <Route path="/robot/auth" element={<AuthStep />} />
        <Route path="/robot/conference" element={<Conference />} />

        {/* 공통 레이아웃을 사용하는 라우터 영역 (예: 대시보드들) */}
        <Route element={<MainLayout />}>
          <Route path="/doctor/dashboard" element={<DoctorDashboard />} />
        </Route>
      </Routes>
    </Router>
  );
}

export default App;
