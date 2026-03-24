import { Navigate, useLocation } from 'react-router-dom';
import useAuthStore from '../../store/authStore';
import { getHomePathForRole } from '../../utils/authRouting';

const ProtectedRoleRoute = ({ allowedRoles = [], children }) => {
    const user = useAuthStore((state) => state.user);
    const location = useLocation();

    if (!user?.role) {
        return <Navigate to="/" replace state={{ from: location }} />;
    }

    if (allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
        return <Navigate to={getHomePathForRole(user.role) || '/'} replace state={{ from: location }} />;
    }

    return children;
};

export default ProtectedRoleRoute;
