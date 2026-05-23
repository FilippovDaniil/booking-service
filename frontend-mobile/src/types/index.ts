export type Role = 'CLIENT' | 'LANDLORD' | 'ADMIN';

export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'EXPIRED'
  | 'CANCELLED_BY_CLIENT'
  | 'CANCELLED_BY_LANDLORD';

export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  blocked: boolean;
}

export interface Apartment {
  id: number;
  title: string;
  description: string;
  city: string;
  address: string;
  pricePerNight: number;
  maxGuests: number;
  rooms: number;
  active: boolean;
  landlordName: string;
  averageRating: number | null;
  reviewCount: number;
}

export interface Booking {
  id: number;
  apartmentId: number;
  apartmentTitle: string;
  city: string;
  checkIn: string;
  checkOut: string;
  totalPrice: number;
  status: BookingStatus;
  createdAt: string;
}

export interface Review {
  id: number;
  authorName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface ApartmentFilters {
  city?: string;
  checkIn?: string;
  checkOut?: string;
  minPrice?: number;
  maxPrice?: number;
  guests?: number;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
