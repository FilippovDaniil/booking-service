import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft, MapPin, Star, Users, BedDouble,
  ChevronDown, ChevronUp,
} from 'lucide-react';
import { apartmentsApi } from '../api/apartments';
import { bookingsApi } from '../api/bookings';
import { useAuthStore } from '../store/authStore';
import { Spinner } from '../components/ui/Spinner';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';

export default function ApartmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user, isAuthenticated } = useAuthStore();

  const [checkIn, setCheckIn] = useState('');
  const [checkOut, setCheckOut] = useState('');
  const [bookingError, setBookingError] = useState('');
  const [bookingSuccess, setBookingSuccess] = useState(false);
  const [showAllReviews, setShowAllReviews] = useState(false);
  const [showBookingForm, setShowBookingForm] = useState(false);

  const aptId = Number(id);

  const { data: apt, isLoading, isError } = useQuery({
    queryKey: ['apartment', aptId],
    queryFn: () => apartmentsApi.getById(aptId),
    enabled: !!aptId,
  });

  const { data: reviews = [] } = useQuery({
    queryKey: ['reviews', aptId],
    queryFn: () => apartmentsApi.getReviews(aptId),
    enabled: !!aptId,
  });

  const bookMutation = useMutation({
    mutationFn: () => bookingsApi.create(aptId, checkIn, checkOut),
    onSuccess: () => {
      setBookingSuccess(true);
      setBookingError('');
      queryClient.invalidateQueries({ queryKey: ['bookings'] });
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message;
      setBookingError(msg ?? 'Не удалось создать бронь');
    },
  });

  const handleBook = () => {
    if (!isAuthenticated()) { navigate('/login', { state: { from: `/apartments/${aptId}` } }); return; }
    if (!checkIn || !checkOut) { setBookingError('Выберите даты'); return; }
    if (checkIn >= checkOut) { setBookingError('Дата выезда должна быть позже заезда'); return; }
    setBookingError('');
    bookMutation.mutate();
  };

  const nights =
    checkIn && checkOut
      ? Math.max(0, Math.ceil((+new Date(checkOut) - +new Date(checkIn)) / 86_400_000))
      : 0;

  const visibleReviews = showAllReviews ? reviews : reviews.slice(0, 3);

  if (isLoading) return <Spinner className="mt-20" />;
  if (isError || !apt) {
    return (
      <div className="p-6 text-center text-gray-500">
        <p>Квартира не найдена</p>
        <button type="button" onClick={() => navigate(-1)} className="mt-3 text-blue-600 text-sm">
          Назад
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col bg-white min-h-dvh pb-28">
      {/* Hero */}
      <div className="relative h-60 bg-gradient-to-br from-blue-100 to-indigo-200 flex items-center justify-center">
        <span className="text-7xl">🏠</span>
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="absolute top-4 left-4 w-9 h-9 bg-white/90 rounded-full flex items-center justify-center shadow-sm"
        >
          <ArrowLeft size={18} className="text-gray-700" />
        </button>
      </div>

      {/* Content */}
      <div className="px-5 py-5 flex flex-col gap-5">
        {/* Title + rating */}
        <div>
          <div className="flex items-start gap-2 justify-between">
            <h1 className="text-xl font-bold text-gray-900 leading-tight flex-1">{apt.title}</h1>
            {apt.averageRating !== null && (
              <div className="flex items-center gap-1 bg-yellow-50 px-2 py-1 rounded-lg shrink-0">
                <Star size={14} className="fill-yellow-400 text-yellow-400" />
                <span className="text-sm font-semibold text-gray-700">
                  {apt.averageRating.toFixed(1)}
                </span>
                <span className="text-xs text-gray-400">({apt.reviewCount})</span>
              </div>
            )}
          </div>
          <div className="flex items-center gap-1.5 mt-2 text-gray-500">
            <MapPin size={14} />
            <span className="text-sm">{apt.city}, {apt.address}</span>
          </div>
        </div>

        {/* Stats */}
        <div className="flex gap-3">
          {[
            { icon: BedDouble, label: `${apt.rooms} комн.` },
            { icon: Users, label: `до ${apt.maxGuests} гостей` },
          ].map(({ icon: Icon, label }) => (
            <div key={label} className="flex items-center gap-2 bg-gray-50 rounded-xl px-3 py-2.5">
              <Icon size={16} className="text-blue-500" />
              <span className="text-sm text-gray-700">{label}</span>
            </div>
          ))}
        </div>

        {/* Price */}
        <div className="flex items-baseline gap-1.5">
          <span className="text-2xl font-bold text-gray-900">
            {apt.pricePerNight.toLocaleString('ru-RU')} ₽
          </span>
          <span className="text-sm text-gray-400">/ ночь</span>
        </div>

        {/* Description */}
        <div>
          <h2 className="text-sm font-semibold text-gray-900 mb-1.5">Описание</h2>
          <p className="text-sm text-gray-600 leading-relaxed">{apt.description}</p>
        </div>

        <div className="text-xs text-gray-400">
          Арендодатель: <span className="text-gray-600">{apt.landlordName}</span>
        </div>

        {/* Divider */}
        <div className="h-px bg-gray-100" />

        {/* Reviews */}
        <div>
          <h2 className="text-sm font-semibold text-gray-900 mb-3">
            Отзывы {reviews.length > 0 && <span className="text-gray-400 font-normal">({reviews.length})</span>}
          </h2>

          {reviews.length === 0 ? (
            <p className="text-sm text-gray-400">Пока нет отзывов</p>
          ) : (
            <div className="flex flex-col gap-3">
              {visibleReviews.map((r) => (
                <div key={r.id} className="bg-gray-50 rounded-2xl p-4">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-sm font-medium text-gray-800">{r.authorName}</span>
                    <div className="flex items-center gap-1">
                      {Array.from({ length: 5 }).map((_, i) => (
                        <Star
                          key={i}
                          size={12}
                          className={i < r.rating ? 'fill-yellow-400 text-yellow-400' : 'text-gray-200'}
                        />
                      ))}
                    </div>
                  </div>
                  <p className="text-sm text-gray-600">{r.comment}</p>
                </div>
              ))}

              {reviews.length > 3 && (
                <button
                  type="button"
                  onClick={() => setShowAllReviews((v) => !v)}
                  className="flex items-center gap-1 text-sm text-blue-600 font-medium"
                >
                  {showAllReviews ? (
                    <><ChevronUp size={16} /> Скрыть</>
                  ) : (
                    <><ChevronDown size={16} /> Показать все ({reviews.length})</>
                  )}
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Booking panel — fixed at bottom */}
      <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[480px] bg-white border-t border-gray-100 px-5 py-4 z-40">
        {bookingSuccess ? (
          <div className="bg-green-50 rounded-2xl px-4 py-3 text-center">
            <p className="text-green-700 font-medium text-sm">✅ Бронь создана!</p>
            <button
              type="button"
              onClick={() => navigate('/bookings')}
              className="mt-1 text-sm text-blue-600 font-medium"
            >
              Перейти к бронированиям
            </button>
          </div>
        ) : !showBookingForm ? (
          <div className="flex items-center justify-between gap-4">
            <div>
              <span className="text-lg font-bold text-gray-900">
                {apt.pricePerNight.toLocaleString('ru-RU')} ₽
              </span>
              <span className="text-xs text-gray-400 ml-1">/ ночь</span>
            </div>
            {user?.role === 'CLIENT' || !isAuthenticated() ? (
              <Button onClick={() => setShowBookingForm(true)}>
                Забронировать
              </Button>
            ) : (
              <span className="text-xs text-gray-400">Недоступно для вашей роли</span>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <div className="flex gap-3">
              <Input
                label="Заезд"
                type="date"
                value={checkIn}
                onChange={(e) => setCheckIn(e.target.value)}
                min={new Date().toISOString().split('T')[0]}
              />
              <Input
                label="Выезд"
                type="date"
                value={checkOut}
                onChange={(e) => setCheckOut(e.target.value)}
                min={checkIn || new Date().toISOString().split('T')[0]}
              />
            </div>

            {nights > 0 && (
              <div className="flex justify-between text-sm text-gray-600">
                <span>{nights} ночей × {apt.pricePerNight.toLocaleString('ru-RU')} ₽</span>
                <span className="font-semibold text-gray-900">
                  {(nights * apt.pricePerNight).toLocaleString('ru-RU')} ₽
                </span>
              </div>
            )}

            {bookingError && (
              <p className="text-xs text-red-500">{bookingError}</p>
            )}

            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setShowBookingForm(false)}>
                Отмена
              </Button>
              <Button fullWidth loading={bookMutation.isPending} onClick={handleBook}>
                Подтвердить
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
