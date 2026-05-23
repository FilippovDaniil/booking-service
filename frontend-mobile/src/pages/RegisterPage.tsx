import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';

type Role = 'CLIENT' | 'LANDLORD';

export default function RegisterPage() {
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [role, setRole] = useState<Role>('CLIENT');
  const [errors, setErrors] = useState<Partial<typeof form & { general: string }>>({});
  const [loading, setLoading] = useState(false);

  const set = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, [field]: e.target.value }));

  const validate = () => {
    const e: typeof errors = {};
    if (!form.firstName.trim()) e.firstName = 'Введите имя';
    if (!form.lastName.trim()) e.lastName = 'Введите фамилию';
    if (!form.email.includes('@')) e.email = 'Некорректный email';
    if (form.password.length < 6) e.password = 'Минимум 6 символов';
    if (form.password !== form.confirmPassword) e.confirmPassword = 'Пароли не совпадают';
    return e;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setErrors({});
    setLoading(true);
    try {
      const tokens = await authApi.register(
        form.email, form.password, form.firstName, form.lastName, role
      );
      const user = await authApi.getMe();
      setAuth(user, tokens.accessToken, tokens.refreshToken);
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message;
      setErrors({ general: msg ?? 'Ошибка регистрации' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-dvh flex flex-col bg-white px-6 pb-8">
      {/* Header */}
      <div className="pt-12 pb-6 text-center">
        <div className="text-4xl mb-3">✨</div>
        <h1 className="text-2xl font-bold text-gray-900">Создать аккаунт</h1>
        <p className="mt-1 text-sm text-gray-500">Быстрая регистрация</p>
      </div>

      {/* Role toggle */}
      <div className="flex rounded-xl bg-gray-100 p-1 mb-5">
        {(['CLIENT', 'LANDLORD'] as Role[]).map((r) => (
          <button
            key={r}
            type="button"
            onClick={() => setRole(r)}
            className={[
              'flex-1 py-2 text-sm font-medium rounded-lg transition-all',
              role === r
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500',
            ].join(' ')}
          >
            {r === 'CLIENT' ? '🔍 Арендатор' : '🏠 Арендодатель'}
          </button>
        ))}
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
        <div className="flex gap-3">
          <Input
            label="Имя"
            placeholder="Иван"
            value={form.firstName}
            onChange={set('firstName')}
            error={errors.firstName}
            autoComplete="given-name"
          />
          <Input
            label="Фамилия"
            placeholder="Иванов"
            value={form.lastName}
            onChange={set('lastName')}
            error={errors.lastName}
            autoComplete="family-name"
          />
        </div>
        <Input
          label="Email"
          type="email"
          placeholder="example@mail.com"
          value={form.email}
          onChange={set('email')}
          error={errors.email}
          inputMode="email"
          autoComplete="email"
        />
        <Input
          label="Пароль"
          type="password"
          placeholder="Минимум 6 символов"
          value={form.password}
          onChange={set('password')}
          error={errors.password}
          autoComplete="new-password"
        />
        <Input
          label="Подтвердите пароль"
          type="password"
          placeholder="••••••••"
          value={form.confirmPassword}
          onChange={set('confirmPassword')}
          error={errors.confirmPassword}
          autoComplete="new-password"
        />

        {errors.general && (
          <div className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">
            {errors.general}
          </div>
        )}

        <Button type="submit" size="lg" fullWidth loading={loading} className="mt-2">
          Зарегистрироваться
        </Button>
      </form>

      <div className="mt-5 text-center text-sm text-gray-500">
        Уже есть аккаунт?{' '}
        <Link to="/login" className="font-medium text-blue-600">
          Войти
        </Link>
      </div>
    </div>
  );
}
