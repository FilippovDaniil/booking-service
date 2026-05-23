import { useNavigate } from 'react-router-dom';
import { MapPin, Star, Users, BedDouble } from 'lucide-react';
import type { Apartment } from '../../types';

export function ApartmentCard({ apt }: { apt: Apartment }) {
  const navigate = useNavigate();

  return (
    <button
      type="button"
      onClick={() => navigate(`/apartments/${apt.id}`)}
      className="w-full text-left bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden active:scale-[0.98] transition-transform"
    >
      {/* Placeholder image */}
      <div className="h-44 bg-gradient-to-br from-blue-100 to-blue-200 flex items-center justify-center">
        <span className="text-5xl">🏠</span>
      </div>

      <div className="p-4">
        {/* Title + rating */}
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-semibold text-gray-900 text-sm leading-snug flex-1">
            {apt.title}
          </h3>
          {apt.averageRating !== null && (
            <div className="flex items-center gap-1 shrink-0">
              <Star size={13} className="fill-yellow-400 text-yellow-400" />
              <span className="text-xs font-medium text-gray-700">
                {apt.averageRating.toFixed(1)}
              </span>
            </div>
          )}
        </div>

        {/* Location */}
        <div className="flex items-center gap-1 mt-1.5 text-gray-500">
          <MapPin size={12} />
          <span className="text-xs">{apt.city}</span>
        </div>

        {/* Params */}
        <div className="flex items-center gap-3 mt-2.5">
          <div className="flex items-center gap-1 text-gray-500">
            <BedDouble size={13} />
            <span className="text-xs">{apt.rooms} комн.</span>
          </div>
          <div className="flex items-center gap-1 text-gray-500">
            <Users size={13} />
            <span className="text-xs">до {apt.maxGuests} гостей</span>
          </div>
        </div>

        {/* Price */}
        <div className="mt-3 flex items-baseline gap-1">
          <span className="text-base font-bold text-gray-900">
            {apt.pricePerNight.toLocaleString('ru-RU')} ₽
          </span>
          <span className="text-xs text-gray-400">/ ночь</span>
        </div>
      </div>
    </button>
  );
}
