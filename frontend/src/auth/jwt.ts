import type { CurrentUser, Role } from '../types';

/** Claims carried by the backend access token (see JwtService on the server). */
export interface JwtClaims {
  sub: string; // user id
  role: Role;
  email: string;
  warehouseIds?: number[];
  exp?: number;
  iat?: number;
  iss?: string;
}

/** Decode a JWT payload (no signature verification — that's the backend's job). */
export function decodeJwt(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}

/** Build the current user from an access token's claims (role comes from the JWT, per RBAC). */
export function userFromToken(token: string | null): CurrentUser | null {
  if (!token) return null;
  const claims = decodeJwt(token);
  if (!claims || !claims.sub || !claims.role) return null;
  return {
    id: Number(claims.sub),
    email: claims.email,
    role: claims.role,
    warehouseIds: claims.warehouseIds ?? [],
  };
}
