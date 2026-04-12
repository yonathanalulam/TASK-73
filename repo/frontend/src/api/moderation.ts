import { http } from './client';

export interface ModerationReport {
  id: number;
  organizationId: number;
  reporterUserId: number;
  targetType: 'POST' | 'COMMENT';
  targetId: number;
  reason: string;
  details: string | null;
  status: 'OPEN' | 'UPHELD' | 'DISMISSED';
  resolutionNotes: string | null;
  reviewedByUserId: number | null;
  createdAt: string;
  resolvedAt: string | null;
}

export const moderationApi = {
  async listOpen(): Promise<ModerationReport[]> {
    const res = await http.get<ModerationReport[]>('/api/moderation/reports');
    return res.data;
  },
  async resolve(id: number, payload: {
    resolution: 'UPHELD' | 'DISMISSED';
    resolutionNotes?: string;
    hideTarget?: boolean;
    hiddenReason?: string;
  }): Promise<ModerationReport> {
    const res = await http.post<ModerationReport>(`/api/moderation/reports/${id}/resolve`, payload);
    return res.data;
  },
  async restore(targetType: 'POST' | 'COMMENT', targetId: number): Promise<void> {
    await http.post('/api/moderation/restore', null, {
      params: { targetType, targetId },
    });
  },
};
