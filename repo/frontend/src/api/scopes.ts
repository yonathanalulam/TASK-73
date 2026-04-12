import { http } from './client';

export interface DataScopeRule {
  id: number;
  userId: number;
  scopeType: 'ORGANIZATION' | 'DEPARTMENT' | 'FACILITY_AREA';
  scopeTargetId: number;
  createdAt: string;
}

export interface ReplaceScopeRulesRequest {
  rules: { scopeType: string; scopeTargetId: number }[];
}

export const scopesApi = {
  async listAll(): Promise<DataScopeRule[]> {
    const res = await http.get<DataScopeRule[]>('/api/scopes');
    return res.data;
  },
  async listForUser(userId: number): Promise<DataScopeRule[]> {
    const res = await http.get<DataScopeRule[]>(`/api/scopes/users/${userId}`);
    return res.data;
  },
  async replaceForUser(userId: number, req: ReplaceScopeRulesRequest): Promise<DataScopeRule[]> {
    const res = await http.put<DataScopeRule[]>(`/api/scopes/users/${userId}`, req);
    return res.data;
  },
};
