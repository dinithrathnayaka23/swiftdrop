import type { Paged } from './parcel';

/** A place a zone covers (CoverageAreaResponse); postalCode null = the whole city. */
export interface CoverageArea {
  id: string;
  district: string;
  city: string;
  postalCode: string | null;
  active: boolean;
}

/** GET /admin/service-areas rows (ServiceAreaResponse). The summary counts active coverage only. */
export interface ServiceAreaRow {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  /** distinct, sorted */
  cities: string[];
  /** distinct, sorted */
  districts: string[];
  /** "lowest-highest", the one code, or null */
  postalCodeRange: string | null;
  /** active coverage rows */
  coverageCount: number;
  createdAt: string;
}

/** GET /admin/service-areas/{id} (ServiceAreaDetailResponse): every coverage row, active or not. */
export interface ServiceAreaDetail extends ServiceAreaRow {
  coverageAreas: CoverageArea[];
}

/** CoverageAreaRequest */
export interface CoverageAreaInput {
  district: string;
  city: string;
  /** optional, 3-15 letters, digits, spaces or hyphens */
  postalCode?: string;
}

/** POST /admin/service-areas (CreateServiceAreaRequest). */
export interface NewServiceArea {
  /** 2-100 characters, unique ignoring case */
  name: string;
  description?: string;
  /** defaults to true */
  active?: boolean;
  /** at most 200 */
  coverageAreas?: CoverageAreaInput[];
}

/** PATCH /admin/service-areas/{id} (UpdateServiceAreaRequest): send at least one. */
export interface ServiceAreaUpdate {
  name?: string;
  /** blank clears it */
  description?: string;
}

export interface ServiceAreaFilters {
  search?: string;
  active?: boolean;
  page?: number;
  /** 1-100 */
  size?: number;
}

export interface ServiceAreasApi {
  list(filters: ServiceAreaFilters): Promise<Paged<ServiceAreaRow>>;
  get(zoneId: string): Promise<ServiceAreaDetail>;
  create(zone: NewServiceArea): Promise<ServiceAreaDetail>;
  update(zoneId: string, update: ServiceAreaUpdate): Promise<ServiceAreaDetail>;
  activate(zoneId: string): Promise<ServiceAreaDetail>;
  deactivate(zoneId: string): Promise<ServiceAreaDetail>;
  addCoverage(zoneId: string, area: CoverageAreaInput): Promise<CoverageArea>;
  removeCoverage(zoneId: string, coverageId: string): Promise<void>;
}
