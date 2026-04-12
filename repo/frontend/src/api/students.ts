import { http } from './client';
import type { PageResponse } from '@/types/api';

export interface Student {
  id: number;
  userId: number | null;
  organizationId: number;
  externalId: string | null;
  fullName: string;
  email: string | null;
  phone: string | null;
  dateOfBirth: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  enrollmentStatus: string;
  skillLevel: string | null;
  notes: string | null;
  school: string | null;
  program: string | null;
  classGroup: string | null;
  housingAssignment: string | null;
  enrolledAt: string | null;
  createdAt: string;
  updatedAt: string;
  masked: boolean;
}

export interface UpdateStudentSelfRequest {
  fullName?: string;
  email?: string;
  phone?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  notes?: string;
}

export interface BulkImportResult {
  jobId: number;
  totalRows: number;
  createdRows: number;
  skippedRows: number;
  failedRows: number;
  sampleErrors: { lineNumber: number; code: string; message: string }[];
  errorReportPath: string | null;
}

export const studentsApi = {
  async list(page = 0, size = 20): Promise<PageResponse<Student>> {
    const res = await http.get<PageResponse<Student>>('/api/students', {
      params: { page, size },
    });
    return res.data;
  },
  async getMyProfile(): Promise<Student> {
    const res = await http.get<Student>('/api/students/me');
    return res.data;
  },
  async updateMyProfile(data: UpdateStudentSelfRequest): Promise<Student> {
    const res = await http.put<Student>('/api/students/me', data);
    return res.data;
  },
  async importTemplate(): Promise<Blob> {
    const res = await http.get('/api/students/import/template', { responseType: 'blob' });
    return res.data;
  },
  async importCsv(organizationId: number, file: File): Promise<BulkImportResult> {
    const form = new FormData();
    form.append('organizationId', String(organizationId));
    form.append('file', file);
    const res = await http.post<BulkImportResult>('/api/students/import', form);
    return res.data;
  },
  async importErrors(jobId: number): Promise<Blob> {
    const res = await http.get(`/api/students/import/${jobId}/errors`, { responseType: 'blob' });
    return res.data;
  },
};
