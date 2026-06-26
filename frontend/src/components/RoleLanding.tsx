import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * Sends an authenticated user to their role's home: CENTRAL_ADMIN → /admin, HUB_PICKER → /picker.
 * Used for `/` and as the post-login destination.
 */
export function RoleLanding() {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={user.role === 'CENTRAL_ADMIN' ? '/admin' : '/picker'} replace />;
}
