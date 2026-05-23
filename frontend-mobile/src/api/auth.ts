import api from './client';
import type { TokenResponse, User } from '../types';

export const authApi = {
  login: (email: string, password: string) =>
    api.post<TokenResponse>('/auth/login', { email, password }).then((r) => r.data),

  register: (email: string, password: string, firstName: string, lastName: string, role: string) =>
    api.post<TokenResponse>('/auth/register', { email, password, firstName, lastName, role }).then((r) => r.data),

  logout: (refreshToken: string) =>
    api.post('/auth/logout', { refreshToken }),

  getMe: () =>
    api.get<User>('/users/me').then((r) => r.data),
};
