import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { TokenResponse } from '../types';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

const ACCESS_KEY = 'shelflife.accessToken';
const REFRESH_KEY = 'shelflife.refreshToken';

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  set: (access: string, refresh: string) => {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

export const http = axios.create({ baseURL: API_BASE_URL });

// Attach the access token to every request.
http.interceptors.request.use((config) => {
  const token = tokenStore.getAccess();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Single in-flight refresh shared across concurrent 401s.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStore.getRefresh();
  if (!refreshToken) {
    throw new Error('No refresh token');
  }
  // Use a bare axios call so this request does NOT pass through the interceptors
  // (avoids recursion and a stale Authorization header).
  const { data } = await axios.post<TokenResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken });
  tokenStore.set(data.accessToken, data.refreshToken);
  return data.accessToken;
}

function redirectToLogin(): void {
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
}

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

// On 401: attempt one silent refresh and replay the request; if that fails, clear
// tokens and redirect to login.
http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const url = original?.url ?? '';
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/refresh');

    if (status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true;
      try {
        if (!refreshPromise) {
          refreshPromise = refreshAccessToken().finally(() => {
            refreshPromise = null;
          });
        }
        const newToken = await refreshPromise;
        original.headers.Authorization = `Bearer ${newToken}`;
        return http(original);
      } catch {
        tokenStore.clear();
        redirectToLogin();
      }
    }
    return Promise.reject(error);
  },
);

export function apiErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined;
    return data?.message ?? error.message ?? fallback;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}

export function statusCode(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}
