import api from './client';
import type { User } from '../types';

export const usersApi = {
  updateMe: (firstName: string, lastName: string) =>
    api.put<User>('/users/me', { firstName, lastName }).then((r) => r.data),
};
