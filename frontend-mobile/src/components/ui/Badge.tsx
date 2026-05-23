import type { BookingStatus } from '../../types';

const statusConfig: Record<BookingStatus, { label: string; className: string }> = {
  PENDING:                  { label: 'Ожидает',     className: 'bg-yellow-100 text-yellow-700' },
  CONFIRMED:                { label: 'Подтверждена', className: 'bg-green-100 text-green-700' },
  COMPLETED:                { label: 'Завершена',   className: 'bg-blue-100 text-blue-700' },
  EXPIRED:                  { label: 'Истекла',     className: 'bg-gray-100 text-gray-600' },
  CANCELLED_BY_CLIENT:      { label: 'Отменена',    className: 'bg-red-100 text-red-600' },
  CANCELLED_BY_LANDLORD:    { label: 'Отменена арендодателем', className: 'bg-red-100 text-red-600' },
};

export function StatusBadge({ status }: { status: BookingStatus }) {
  const cfg = statusConfig[status] ?? { label: status, className: 'bg-gray-100 text-gray-600' };
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${cfg.className}`}>
      {cfg.label}
    </span>
  );
}
