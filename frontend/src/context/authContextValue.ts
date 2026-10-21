import { createContext } from 'react';
import type { AuthSession, AuthUser } from '../types/auth';

export interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (session: AuthSession) => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
