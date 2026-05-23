import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { setAuth } = useAuthStore();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const from = (location.state as { from?: string })?.from ?? '/';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Заполните все поля');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const tokens = await authApi.login(email, password);
      const user = await authApi.getMe();
      setAuth(user, tokens.accessToken, tokens.refreshToken);
      navigate(from, { replace: true });
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message;
      setError(msg ?? 'Неверный email или пароль');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-dvh flex flex-col bg-white px-6">
      {/* Header */}
      <div className="pt-16 pb-8 text-center">
        <div className="text-4xl mb-3">🏠</div>
        <h1 className="text-2xl font-bold text-gray-900">Добро пожаловать</h1>
        <p className="mt-1 text-sm text-gray-500">Войдите в свой аккаунт</p>
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input
          label="Email"
          type="email"
          placeholder="example@mail.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          inputMode="email"
        />
        <Input
          label="Пароль"
          type="password"
          placeholder="••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        {error && (
          <div className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">
            {error}
          </div>
        )}

        <Button type="submit" size="lg" fullWidth loading={loading} className="mt-2">
          Войти
        </Button>
      </form>

      {/* Footer */}
      <div className="mt-6 text-center text-sm text-gray-500">
        Нет аккаунта?{' '}
        <Link to="/register" className="font-medium text-blue-600">
          Зарегистрироваться
        </Link>
      </div>
    </div>
  );
}
