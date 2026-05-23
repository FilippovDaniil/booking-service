import api from './client';
import type { Apartment, ApartmentFilters, PageResponse, Review } from '../types';

export const apartmentsApi = {
  search: (filters: ApartmentFilters) =>
    api.get<PageResponse<Apartment>>('/apartments', { params: filters }).then((r) => r.data),

  getById: (id: number) =>
    api.get<Apartment>(`/apartments/${id}`).then((r) => r.data),

  getReviews: (apartmentId: number) =>
    api.get<Review[]>(`/apartments/${apartmentId}/reviews`).then((r) => r.data),

  getMy: () =>
    api.get<Apartment[]>('/apartments/my').then((r) => r.data),
};
