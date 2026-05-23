import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { SlidersHorizontal, X } from 'lucide-react';
import { apartmentsApi } from '../api/apartments';
import { ApartmentCard } from '../components/ui/ApartmentCard';
import { Spinner } from '../components/ui/Spinner';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import type { ApartmentFilters } from '../types';

export default function SearchPage() {
  const [filters, setFilters] = useState<ApartmentFilters>({ size: 20 });
  const [draft, setDraft] = useState<ApartmentFilters>({ size: 20 });
  const [showFilters, setShowFilters] = useState(false);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['apartments', filters],
    queryFn: () => apartmentsApi.search(filters),
  });

  const applyFilters = () => {
    setFilters({ ...draft });
    setShowFilters(false);
  };

  const resetFilters = () => {
    const empty = { size: 20 };
    setDraft(empty);
    setFilters(empty);
    setShowFilters(false);
  };

  const hasActiveFilters =
    !!(filters.city || filters.checkIn || filters.minPrice || filters.maxPrice || filters.guests);

  return (
    <div className="flex flex-col min-h-dvh bg-gray-50">
      {/* Top bar */}
      <div className="sticky top-0 z-10 bg-white border-b border-gray-100 px-4 py-3">
        <div className="flex items-center justify-between mb-3">
          <h1 className="text-lg font-bold text-gray-900">🏠 Поиск жилья</h1>
          <button
            type="button"
            onClick={() => setShowFilters(true)}
            className="relative flex items-center gap-1.5 text-sm font-medium text-gray-700 bg-gray-100 rounded-xl px-3 py-2 active:bg-gray-200"
          >
            <SlidersHorizontal size={15} />
            Фильтры
            {hasActiveFilters && (
              <span className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-blue-600 text-white text-[10px] flex items-center justify-center">
                !
              </span>
            )}
          </button>
        </div>

        {/* Quick city search */}
        <input
          type="text"
          placeholder="🔍  Поиск по городу..."
          value={draft.city ?? ''}
          onChange={(e) => {
            const city = e.target.value || undefined;
            setDraft((p) => ({ ...p, city }));
            setFilters((p) => ({ ...p, city }));
          }}
          className="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-2.5 text-sm outline-none focus:border-blue-400 focus:bg-white"
        />
      </div>

      {/* Results */}
      <div className="flex-1 px-4 py-4">
        {isLoading && <Spinner />}

        {isError && (
          <div className="text-center py-12 text-gray-500 text-sm">
            Не удалось загрузить данные. Проверьте соединение.
          </div>
        )}

        {!isLoading && !isError && data?.content.length === 0 && (
          <div className="text-center py-16">
            <div className="text-4xl mb-3">🔍</div>
            <p className="text-gray-500 text-sm">Квартиры не найдены</p>
            <button
              type="button"
              onClick={resetFilters}
              className="mt-3 text-blue-600 text-sm font-medium"
            >
              Сбросить фильтры
            </button>
          </div>
        )}

        {data && data.content.length > 0 && (
          <>
            <p className="text-xs text-gray-400 mb-3">
              Найдено: {data.totalElements} квартир
            </p>
            <div className="flex flex-col gap-4">
              {data.content.map((apt) => (
                <ApartmentCard key={apt.id} apt={apt} />
              ))}
            </div>
          </>
        )}
      </div>

      {/* Filter drawer */}
      {showFilters && (
        <div className="fixed inset-0 z-50 flex flex-col justify-end">
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setShowFilters(false)}
          />
          <div className="relative bg-white rounded-t-3xl px-5 pb-8 pt-5 max-h-[80vh] overflow-y-auto">
            {/* Handle */}
            <div className="w-10 h-1 bg-gray-200 rounded-full mx-auto mb-5" />

            <div className="flex items-center justify-between mb-5">
              <h2 className="text-base font-bold text-gray-900">Фильтры</h2>
              <button type="button" onClick={() => setShowFilters(false)}>
                <X size={20} className="text-gray-400" />
              </button>
            </div>

            <div className="flex flex-col gap-4">
              <Input
                label="Город"
                placeholder="Москва"
                value={draft.city ?? ''}
                onChange={(e) => setDraft((p) => ({ ...p, city: e.target.value || undefined }))}
              />

              <div className="flex gap-3">
                <Input
                  label="Дата заезда"
                  type="date"
                  value={draft.checkIn ?? ''}
                  onChange={(e) => setDraft((p) => ({ ...p, checkIn: e.target.value || undefined }))}
                />
                <Input
                  label="Дата выезда"
                  type="date"
                  value={draft.checkOut ?? ''}
                  onChange={(e) => setDraft((p) => ({ ...p, checkOut: e.target.value || undefined }))}
                />
              </div>

              <div className="flex gap-3">
                <Input
                  label="Цена от, ₽"
                  type="number"
                  inputMode="numeric"
                  placeholder="0"
                  value={draft.minPrice ?? ''}
                  onChange={(e) =>
                    setDraft((p) => ({ ...p, minPrice: e.target.value ? +e.target.value : undefined }))
                  }
                />
                <Input
                  label="Цена до, ₽"
                  type="number"
                  inputMode="numeric"
                  placeholder="∞"
                  value={draft.maxPrice ?? ''}
                  onChange={(e) =>
                    setDraft((p) => ({ ...p, maxPrice: e.target.value ? +e.target.value : undefined }))
                  }
                />
              </div>

              <Input
                label="Гостей"
                type="number"
                inputMode="numeric"
                placeholder="1"
                value={draft.guests ?? ''}
                onChange={(e) =>
                  setDraft((p) => ({ ...p, guests: e.target.value ? +e.target.value : undefined }))
                }
              />
            </div>

            <div className="flex gap-3 mt-6">
              <Button variant="secondary" fullWidth onClick={resetFilters}>
                Сбросить
              </Button>
              <Button fullWidth onClick={applyFilters}>
                Применить
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
