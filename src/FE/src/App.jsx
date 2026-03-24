import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import ProtectedRoleRoute from './components/auth/ProtectedRoleRoute';
import Login from './pages/Auth/Login';
import Signup from './pages/Auth/Signup';
import VideoConference from './pages/Doctor/VideoConference';
import ControlCenter from './pages/Operator/ControlCenter';
import AdminLogin from './pages/Auth/AdminLogin';
import AdminSignup from './pages/Auth/AdminSignup';
import PatientPortal from './pages/Patient/Portal';
import PatientMyPage from './pages/Patient/MyPage';
import RobotHome from './pages/Robot/Home';
import Setup from './pages/Robot/Setup';
import AuthStep from './pages/Robot/AuthStep';
import MeasureIntro from './pages/Robot/Measurement/MeasureIntro';
import Temperature from './pages/Robot/Measurement/Temperature';
import Blood from './pages/Robot/Measurement/Blood';
import SpO2 from './pages/Robot/Measurement/SpO2';
import Ecg from './pages/Robot/Measurement/Ecg';
import Conference from './pages/Robot/Conference';
import Finish from './pages/Robot/Finish';
import MainEntrance from './pages/Home/MainEntrance';

// Mock EMR
import EMRLogin from './pages/Doctor/EMR/Login';
import EMRDashboard from './pages/Doctor/EMR/Dashboard';

function App() {
    return (
        <Router>
            <Routes>
                {/* 메인 진입 페이지 */}
                <Route path="/" element={<MainEntrance />} />
                <Route path="/login" element={<Login />} />
                <Route path="/signup" element={<Signup />} />

                {/* 관리자 전용 인증 페이지 */}
                <Route path="/operator/login" element={<AdminLogin />} />
                <Route path="/operator/signup" element={<AdminSignup />} />

                {/* 화상 진료 페이지 (사이드바 없이 전체화면) */}
                <Route
                    path="/doctor/consultation/:id"
                    element={(
                        <ProtectedRoleRoute allowedRoles={['DOCTOR']}>
                            <VideoConference />
                        </ProtectedRoleRoute>
                    )}
                />

                {/* 관제/운영 풀스크린 단독화면 */}
                <Route
                    path="/operator/control"
                    element={(
                        <ProtectedRoleRoute allowedRoles={['ADMIN']}>
                            <ControlCenter />
                        </ProtectedRoleRoute>
                    )}
                />

                {/* 의사용 외부 EMR 모의 시스템 */}
                <Route path="/emr/login" element={<EMRLogin />} />
                <Route
                    path="/emr/dashboard"
                    element={(
                        <ProtectedRoleRoute allowedRoles={['DOCTOR']}>
                            <EMRDashboard />
                        </ProtectedRoleRoute>
                    )}
                />

                {/* 환자/보호자 전용 포털 화면 */}
                <Route
                    path="/patient/portal"
                    element={(
                        <ProtectedRoleRoute allowedRoles={['GUARDIAN']}>
                            <PatientPortal />
                        </ProtectedRoleRoute>
                    )}
                />
                <Route
                    path="/patient/mypage"
                    element={(
                        <ProtectedRoleRoute allowedRoles={['GUARDIAN']}>
                            <PatientMyPage />
                        </ProtectedRoleRoute>
                    )}
                />

                {/* 로봇 (환자) 전용 화면 */}
                <Route path="/robot" element={<RobotHome />} />
                <Route path="/robot/setup" element={<Setup />} />
                <Route path="/robot/auth" element={<AuthStep />} />
                <Route path="/robot/measure-intro" element={<MeasureIntro />} />
                <Route path="/robot/measure/temperature" element={<Temperature />} />
                <Route path="/robot/measure/blood" element={<Blood />} />
                <Route path="/robot/measure/spo2" element={<SpO2 />} />
                <Route path="/robot/measure/ecg" element={<Ecg />} />
                <Route path="/robot/conference" element={<Conference />} />
                <Route path="/robot/finish" element={<Finish />} />
            </Routes>
        </Router>
    );
}

export default App;
