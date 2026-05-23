import api from './client';
import type { Booking } from '../types';

export const bookingsApi = {
  create: (apartmentId: number, checkIn: string, checkOut: string) =>
    api.post<Booking>('/bookings', { apartmentId, checkIn, checkOut }).then((r) => r.data),

  getMy: () =>
    api.get<Booking[]>('/bookings').then((r) => r.data),

  getById: (id: number) =>
    api.get<Booking>(`/bookings/${id}`).then((r) => r.data),

  cancel: (id: number) =>
    api.post(`/bookings/${id}/cancel`),

  confirm: (id: number) =>
    api.post(`/bookings/${id}/confirm`),
};
