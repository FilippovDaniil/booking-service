import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CalendarDays, MapPin, ChevronRight } from 'lucide-react';
import { bookingsApi } from '../api/bookings';
import { Spinner } from '../components/ui/Spinner';
import { StatusBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import type { Booking, BookingStatus } from '../types';

const STATUS_TABS: { label: string; statuses: BookingStatus[] | null }[] = [
  { label: 'Все',       statuses: null },
  { label: 'Активные',  statuses: ['PENDING', 'CONFIRMED'] },
  { label: 'Завершённые', statuses: ['COMPLETED'] },
  { label: 'Отменённые', statuses: ['CANCELLED_BY_CLIENT', 'CANCELLED_BY_LANDLORD', 'EXPIRED'] },
];

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

function BookingCard({
  booking,
  onCancel,
  cancelling,
}: {
  booking: Booking;
  onCancel: (id: number) => void;
  cancelling: boolean;
}) {
  const navigate = useNavigate();
  const nights = Math.ceil(
    (+new Date(booking.checkOut) - +new Date(booking.checkIn)) / 86_400_000
  );
  const canCancel = booking.status === 'PENDING' || booking.status === 'CONFIRMED';

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
      {/* Header */}
      <button
        type="button"
        onClick={() => navigate(`/apartments/${booking.apartmentId}`)}
        className="w-full flex items-start gap-3 p-4 active:bg-gray-50"
      >
        <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-blue-100 to-indigo-200 flex items-center justify-center shrink-0 text-2xl">
          🏠
        </div>
        <div className="flex-1 text-left min-w-0">
          <p className="font-semibold text-gray-900 text-sm truncate">{booking.apartmentTitle}</p>
          <div className="flex items-center gap-1 mt-0.5 text-gray-500">
            <MapPin size={11} />
            <span className="text-xs">{booking.city}</span>
          </div>
          <div className="mt-1">
            <StatusBadge status={booking.status} />
          </div>
        </div>
        <ChevronRight size={16} className="text-gray-300 mt-1 shrink-0" />
      </button>

      {/* Dates + price */}
      <div className="px-4 pb-3 flex items-center justify-between">
        <div className="flex items-center gap-2 text-gray-600">
          <CalendarDays size={14} />
          <span className="text-xs">
            {formatDate(booking.checkIn)} — {formatDate(booking.checkOut)}
          </span>
          <span className="text-xs text-gray-400">({nights} ночей)</span>
        </div>
        <span className="text-sm font-bold text-gray-900">
          {booking.totalPrice.toLocaleString('ru-RU')} ₽
        </span>
      </div>

      {/* Cancel button */}
      {canCancel && (
        <div className="px-4 pb-4">
          <Button
            variant="danger"
            size="sm"
            fullWidth
            loading={cancelling}
            onClick={() => onCancel(booking.id)}
          >
            Отменить бронь
          </Button>
        </div>
      )}
    </div>
  );
}

export default function BookingsPage() {
  const [activeTab, setActiveTab] = useState(0);
  const queryClient = useQueryClient();

  const { data: bookings = [], isLoading, isError } = useQuery({
    queryKey: ['bookings'],
    queryFn: bookingsApi.getMy,
  });

  const cancelMutation = useMutation({
    mutationFn: bookingsApi.cancel,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['bookings'] }),
  });

  const filtered = bookings.filter((b) => {
    const tab = STATUS_TABS[activeTab];
    return !tab.statuses || tab.statuses.includes(b.status);
  });

  return (
    <div className="flex flex-col bg-gray-50 min-h-dvh">
      {/* Header */}
      <div className="sticky top-0 z-10 bg-white border-b border-gray-100 px-4 pt-4 pb-0">
        <h1 className="text-lg font-bold text-gray-900 mb-3">Мои бронирования</h1>

        {/* Tabs */}
        <div className="flex gap-1 overflow-x-auto pb-3 scrollbar-hide">
          {STATUS_TABS.map((tab, i) => (
            <button
              key={i}
              type="button"
              onClick={() => setActiveTab(i)}
              className={[
                'shrink-0 px-3 py-1.5 rounded-full text-xs font-medium transition-colors whitespace-nowrap',
                activeTab === i
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600',
              ].join(' ')}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 px-4 py-4">
        {isLoading && <Spinner />}

        {isError && (
          <p className="text-center text-sm text-gray-500 mt-12">
            Не удалось загрузить бронирования
          </p>
        )}

        {!isLoading && !isError && filtered.length === 0 && (
          <div className="text-center py-16">
            <div className="text-4xl mb-3">📅</div>
            <p className="text-gray-500 text-sm">Нет бронирований</p>
          </div>
        )}

        {filtered.length > 0 && (
          <div className="flex flex-col gap-3">
            {filtered.map((b: Booking) => (
              <BookingCard
                key={b.id}
                booking={b}
                onCancel={(id) => cancelMutation.mutate(id)}
                cancelling={cancelMutation.isPending && cancelMutation.variables === b.id}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
