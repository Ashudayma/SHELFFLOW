import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/api';
import { tokenStore } from '../api/http';
import type { CurrentUser } from '../types';
import { userFromToken } from './jwt';

interface AuthContextValue {
  user: CurrentUser | null;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  // Resolve the session synchronously from the stored access token: identity and role
  // come from the decoded JWT claims (the backend signs them; we only read them here).
  const [user, setUser] = useState<CurrentUser | null>(() => userFromToken(tokenStore.getAccess()));
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const login = useCallback(async (usernameOrEmail: string, password: string) => {
    const tokens = await authApi.login(usernameOrEmail, password);
    tokenStore.set(tokens.accessToken, tokens.refreshToken);
    const me = userFromToken(tokens.accessToken);
    if (!me) {
      tokenStore.clear();
      throw new Error('Login succeeded but the token could not be read.');
    }
    // No role gating here — both roles may sign in; routing decides where they land/can go.
    setUser(me);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefresh();
    try {
      if (refreshToken) await authApi.logout(refreshToken);
    } catch {
      // best-effort; clear local state regardless
    }
    tokenStore.clear();
    setUser(null);
    queryClient.clear();
    navigate('/login', { replace: true });
  }, [navigate, queryClient]);

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
