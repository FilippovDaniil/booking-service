import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';

export function useInitAuth() {
  const { accessToken, setUser, logout } = useAuthStore();

  const { data, isError } = useQuery({
    queryKey: ['me'],
    queryFn: authApi.getMe,
    enabled: !!accessToken,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  useEffect(() => {
    if (data) setUser(data);
  }, [data, setUser]);

  useEffect(() => {
    if (isError) logout();
  }, [isError, logout]);
}
