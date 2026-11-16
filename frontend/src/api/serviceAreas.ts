import type { Paged } from '../types/parcel';
import type {
  CoverageArea,
  ServiceAreaDetail,
  ServiceAreaRow,
  ServiceAreasApi,
} from '../types/serviceArea';
import { isMockApiEnabled } from '../utils/env';
import { apiClient } from './client';
import { request } from './errors';
import { mockServiceAreasApi } from './mock/mockServiceAreasApi';

const BASE = '/admin/service-areas';

function path(zoneId: string, suffix = ''): string {
  return `${BASE}/${encodeURIComponent(zoneId)}${suffix}`;
}

/** The admin Service Areas API (config module, DISPATCHER_ADMIN). See docs/config.md. */
export const httpServiceAreasApi: ServiceAreasApi = {
  list: (filters) => request(() => apiClient.get<Paged<ServiceAreaRow>>(BASE, { params: filters })),

  get: (zoneId) => request(() => apiClient.get<ServiceAreaDetail>(path(zoneId))),

  create: (zone) => request(() => apiClient.post<ServiceAreaDetail>(BASE, zone)),

  update: (zoneId, update) =>
    request(() => apiClient.patch<ServiceAreaDetail>(path(zoneId), update)),

  activate: (zoneId) => request(() => apiClient.post<ServiceAreaDetail>(path(zoneId, '/activate'))),

  deactivate: (zoneId) =>
    request(() => apiClient.post<ServiceAreaDetail>(path(zoneId, '/deactivate'))),

  addCoverage: (zoneId, area) =>
    request(() => apiClient.post<CoverageArea>(path(zoneId, '/coverage-areas'), area)),

  removeCoverage: async (zoneId, coverageId) => {
    await request(() =>
      apiClient.delete<void>(path(zoneId, `/coverage-areas/${encodeURIComponent(coverageId)}`)),
    );
  },
};

export const serviceAreasApi: ServiceAreasApi = isMockApiEnabled()
  ? mockServiceAreasApi
  : httpServiceAreasApi;
