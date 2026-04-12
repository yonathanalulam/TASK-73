import { http } from './client';

export type SessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type SessionType = 'VENUE' | 'ONLINE';
export type BookingStatus = 'INITIATED' | 'CONFIRMED' | 'BOOKED' | 'CHECKED_IN' | 'CANCELED' | 'CANCELLED' | 'REFUNDED' | 'NO_SHOW';

export interface TrainingSession {
  id: number;
  trainingClassId: number;
  organizationId: number;
  startsAt: string;
  endsAt: string;
  location: string | null;
  instructorUserId: number | null;
  capacity: number;
  bookedSeats: number;
  status: SessionStatus;
  notes: string | null;
  sessionType: SessionType | null;
  onlineUrl: string | null;
  level: string | null;
  weightClassLbs: number | null;
  style: string | null;
  createdAt: string;
  cancelledAt: string | null;
}

export interface Booking {
  id: number;
  organizationId: number;
  sessionId: number;
  studentId: number;
  status: BookingStatus;
  sessionType: string | null;
  refundCreditTxId: number | null;
  notes: string | null;
  checkedInAt: string | null;
  createdAt: string;
  cancelledAt: string | null;
}

export interface CreateBookingRequest {
  trainingSessionId: number;
  studentId: number;
  notes?: string;
}

export const trainingApi = {
  async listSessions(params?: Record<string, string>): Promise<TrainingSession[]> {
    const res = await http.get<TrainingSession[]>('/api/training/sessions', { params });
    return res.data;
  },
  async listMyBookings(): Promise<Booking[]> {
    const res = await http.get<Booking[]>('/api/bookings/mine');
    return res.data;
  },
  async listBookings(params: { sessionId?: number; studentId?: number }): Promise<Booking[]> {
    const res = await http.get<Booking[]>('/api/bookings', { params });
    return res.data;
  },
  async createBooking(req: CreateBookingRequest): Promise<Booking> {
    const res = await http.post<Booking>('/api/bookings', req);
    return res.data;
  },
  async confirmBooking(id: number): Promise<Booking> {
    const res = await http.post<Booking>(`/api/bookings/${id}/confirm`);
    return res.data;
  },
  async cancelBooking(id: number): Promise<Booking> {
    const res = await http.delete<Booking>(`/api/bookings/${id}`);
    return res.data;
  },
  async refundBooking(id: number, creditAmount: number, notes?: string): Promise<Booking> {
    const res = await http.post<Booking>(`/api/bookings/${id}/refund`, { creditAmount, notes });
    return res.data;
  },
};
