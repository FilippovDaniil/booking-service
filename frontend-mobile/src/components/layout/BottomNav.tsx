import { NavLink } from 'react-router-dom';
import { Search, CalendarDays, User } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';

const tabs = [
  { to: '/',         icon: Search,      label: 'Поиск' },
  { to: '/bookings', icon: CalendarDays, label: 'Брони' },
  { to: '/profile',  icon: User,        label: 'Профиль' },
];

export function BottomNav() {
  const { isAuthenticated } = useAuthStore();

  return (
    <nav className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[480px] bg-white border-t border-gray-100 flex z-50">
      {tabs.map(({ to, icon: Icon, label }) => {
        if (to === '/bookings' && !isAuthenticated()) return null;
        return (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              [
                'flex-1 flex flex-col items-center gap-0.5 py-2.5 text-xs transition-colors',
                isActive ? 'text-blue-600' : 'text-gray-400',
              ].join(' ')
            }
          >
            <Icon size={22} strokeWidth={1.8} />
            {label}
          </NavLink>
        );
      })}
    </nav>
  );
}
