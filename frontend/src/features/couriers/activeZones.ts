import { serviceAreasApi } from '../../api/serviceAreas';
import type { ServiceAreaRow } from '../../types/serviceArea';
import { useApiData } from '../shared-parcels/useApiData';

/** One call: the active service areas (the API allows up to 50 per page here). */
const ACTIVE_ZONES_QUERY = { active: true, size: 50 } as const;

/** Active service areas, for the picker and for dropping inactive ids before saving. */
export function useActiveZones() {
  return useApiData('active-zones', () => serviceAreasApi.list(ACTIVE_ZONES_QUERY));
}

export type ActiveZonesState = ReturnType<typeof useActiveZones>;

/**
 * The ids to send: only active zones. PATCH replaces the whole set and an inactive id is a 400, so a
 * zone deactivated since it was given to the courier is dropped.
 */
export function keepActive(ids: string[], activeZones: ServiceAreaRow[]): string[] {
  const active = new Set(activeZones.map((zone) => zone.id));
  return ids.filter((id) => active.has(id));
}
