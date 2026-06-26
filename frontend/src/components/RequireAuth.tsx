import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * Authentication gate. Renders the matched child route when a session exists (derived from the
 * stored JWT), otherwise redirects to /login. Role checks happen in {@link RequireRole}.
 */
export function RequireAuth() {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
