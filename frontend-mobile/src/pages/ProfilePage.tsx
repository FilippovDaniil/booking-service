import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LogOut, ChevronRight, User, Shield, Edit2, Check, X } from 'lucide-react';
import { authApi } from '../api/auth';
import { usersApi } from '../api/users';
import { useAuthStore } from '../store/authStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';

const ROLE_LABEL: Record<string, string> = {
  CLIENT:   '🔍 Арендатор',
  LANDLORD: '🏠 Арендодатель',
  ADMIN:    '⚙️ Администратор',
};

export default function ProfilePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, logout, setUser } = useAuthStore();

  const [editing, setEditing] = useState(false);
  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [editError, setEditError] = useState('');

  const updateMutation = useMutation({
    mutationFn: () => usersApi.updateMe(firstName, lastName),
    onSuccess: (updated) => {
      setUser(updated);
      setEditing(false);
      setEditError('');
      queryClient.invalidateQueries({ queryKey: ['me'] });
    },
    onError: () => setEditError('Не удалось сохранить изменения'),
  });

  const handleLogout = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      try { await authApi.logout(refreshToken); } catch { /* ignore */ }
    }
    logout();
    navigate('/login', { replace: true });
  };

  if (!user) return null;

  return (
    <div className="flex flex-col bg-gray-50 min-h-dvh">
      {/* Header */}
      <div className="bg-white px-5 pt-10 pb-6 text-center border-b border-gray-100">
        {/* Avatar */}
        <div className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-400 to-indigo-500 flex items-center justify-center mx-auto mb-3 text-white text-3xl font-bold shadow-md">
          {user.firstName.charAt(0).toUpperCase()}
        </div>

        {!editing ? (
          <>
            <h2 className="text-xl font-bold text-gray-900">
              {user.firstName} {user.lastName}
            </h2>
            <p className="text-sm text-gray-500 mt-0.5">{user.email}</p>
            <span className="mt-2 inline-block text-xs bg-blue-50 text-blue-700 px-3 py-1 rounded-full font-medium">
              {ROLE_LABEL[user.role] ?? user.role}
            </span>
            <button
              type="button"
              onClick={() => { setFirstName(user.firstName); setLastName(user.lastName); setEditing(true); }}
              className="mt-3 flex items-center gap-1 text-sm text-blue-600 mx-auto"
            >
              <Edit2 size={14} /> Редактировать
            </button>
          </>
        ) : (
          <div className="mt-2 flex flex-col gap-3 text-left">
            <div className="flex gap-3">
              <Input
                label="Имя"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
              />
              <Input
                label="Фамилия"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
              />
            </div>
            {editError && <p className="text-xs text-red-500">{editError}</p>}
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                fullWidth
                onClick={() => { setEditing(false); setEditError(''); }}
              >
                <X size={14} className="inline mr-1" />Отмена
              </Button>
              <Button
                size="sm"
                fullWidth
                loading={updateMutation.isPending}
                onClick={() => updateMutation.mutate()}
              >
                <Check size={14} className="inline mr-1" />Сохранить
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Menu */}
      <div className="px-4 py-4 flex flex-col gap-2">
        {/* Info block */}
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <MenuItem icon={<User size={16} className="text-blue-500" />} label="Email" value={user.email} />
          <div className="h-px bg-gray-50 mx-4" />
          <MenuItem
            icon={<Shield size={16} className="text-indigo-500" />}
            label="Роль"
            value={ROLE_LABEL[user.role] ?? user.role}
          />
        </div>

        {/* Logout */}
        <button
          type="button"
          onClick={handleLogout}
          className="mt-2 w-full flex items-center gap-3 bg-white rounded-2xl border border-gray-100 px-4 py-4 active:bg-red-50 transition-colors"
        >
          <div className="w-8 h-8 rounded-xl bg-red-50 flex items-center justify-center">
            <LogOut size={16} className="text-red-500" />
          </div>
          <span className="text-sm font-medium text-red-500 flex-1 text-left">
            Выйти из аккаунта
          </span>
        </button>
      </div>

      <p className="text-center text-xs text-gray-300 mt-auto pb-6">
        Pet Booking v1.0
      </p>
    </div>
  );
}

function MenuItem({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 px-4 py-3.5">
      <div className="w-8 h-8 rounded-xl bg-gray-50 flex items-center justify-center">
        {icon}
      </div>
      <div className="flex-1">
        <p className="text-xs text-gray-400">{label}</p>
        <p className="text-sm font-medium text-gray-800">{value}</p>
      </div>
      <ChevronRight size={14} className="text-gray-300" />
    </div>
  );
}
