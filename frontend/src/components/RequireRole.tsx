import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../types';

/**
 * Role guard mirroring the backend RBAC. Assumes an authenticated user (nested under
 * {@link RequireAuth}). If the user's JWT role doesn't match {@code allow}, the access attempt
 * is rejected to the 403 page (e.g. a HUB_PICKER manually opening /admin/users).
 */
export function RequireRole({ allow }: { allow: Role }) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.role !== allow) {
    return <Navigate to="/403" replace />;
  }
  return <Outlet />;
}
