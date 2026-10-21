import type { HealthResponse } from '../types/api';
import { apiClient } from './client';

export async function getHealth(): Promise<HealthResponse> {
  const { data } = await apiClient.get<HealthResponse>('/health');
  return data;
}
