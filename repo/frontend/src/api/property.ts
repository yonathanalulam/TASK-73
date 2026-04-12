import { http } from './client';

export interface Property {
  id: number;
  organizationId: number;
  code: string;
  name: string;
  address: string | null;
  description: string | null;
  policies: string | null;
  active: boolean;
  createdAt: string;
}

export interface RoomType {
  id: number;
  propertyId: number;
  code: string;
  name: string;
  description: string | null;
  maxOccupancy: number;
  baseRateCents: number;
  features: string | null;
}

export interface PropertyAmenity {
  id: number;
  propertyId: number;
  code: string;
  label: string;
  icon: string | null;
}

export const propertyApi = {
  async list(): Promise<Property[]> {
    const res = await http.get<Property[]>('/api/property');
    return res.data;
  },
  async listAmenities(propertyId: number): Promise<PropertyAmenity[]> {
    const res = await http.get<PropertyAmenity[]>(`/api/property/${propertyId}/amenities`);
    return res.data;
  },
  async listRoomTypes(propertyId: number): Promise<RoomType[]> {
    const res = await http.get<RoomType[]>(`/api/property/${propertyId}/room-types`);
    return res.data;
  },
};
