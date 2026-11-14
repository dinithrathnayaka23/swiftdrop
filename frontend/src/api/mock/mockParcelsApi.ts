import type {
  AdminParcelCounts,
  AdminParcelDetail,
  AdminParcelFilters,
  AdminParcelTab,
  CustomerParcelDetail,
  CustomerParcelFilters,
  CustomerParcelSummary,
  DispatchState,
  Paged,
  ParcelsApi,
  ParcelType,
  StatusCounts,
  StatusHistoryItem,
} from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';
import { delay } from './delay';

/** Sample data for the parcel screens until the backend endpoints exist. */

const FLOW: ParcelStatusCode[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString();
}

/** The sample reason recorded with each failure event. */
const FAILURE_REASONS: Partial<Record<ParcelStatusCode, string>> = {
  FAILED_ATTEMPT: 'Recipient not available',
  FAILED: 'Delivery closed after a failed attempt',
};

/**
 * CREATED ... status, one step every few hours from {@code bookedHoursAgo}. A failed attempt comes
 * after going out; FAILED is a failed attempt the dispatcher then closed.
 */
function historyUpTo(
  status: ParcelStatusCode,
  bookedHoursAgo: number,
  city: string,
): StatusHistoryItem[] {
  let steps: ParcelStatusCode[];
  if (status === 'FAILED_ATTEMPT') steps = [...FLOW.slice(0, 4), 'FAILED_ATTEMPT'];
  else if (status === 'FAILED') steps = [...FLOW.slice(0, 4), 'FAILED_ATTEMPT', 'FAILED'];
  else steps = FLOW.slice(0, FLOW.indexOf(status) + 1);
  const gap = Math.max(1, Math.min(4, bookedHoursAgo / (steps.length + 1)));
  return steps.map((step, i) => ({
    status: step,
    changedAt: hoursAgo(bookedHoursAgo - i * gap),
    note: step === 'IN_TRANSIT' ? `Your parcel is now in transit to ${city}.` : null,
    failureReason: FAILURE_REASONS[step] ?? null,
  }));
}

function page<T>(items: T[], pageIndex = 0, size = 10): Paged<T> {
  const start = pageIndex * size;
  return {
    content: items.slice(start, start + size),
    page: {
      size,
      number: pageIndex,
      totalElements: items.length,
      totalPages: Math.max(1, Math.ceil(items.length / size)),
    },
  };
}

function inDateRange(iso: string, from?: string, to?: string): boolean {
  const day = iso.slice(0, 10);
  return (!from || day >= from) && (!to || day <= to);
}

function matches(search: string | undefined, ...values: (string | null | undefined)[]): boolean {
  const term = search?.trim().toLowerCase();
  return !term || values.some((value) => value?.toLowerCase().includes(term));
}

// ---- Customer ------------------------------------------------------------------------------

interface CustomerSample {
  trackingNumber: string;
  city: string;
  district: string;
  status: ParcelStatusCode;
  fee: number;
  bookedHoursAgo: number;
  recipient: string;
  street: string;
  postalCode: string;
  phone: string;
  parcelType: ParcelType;
  contents: string;
  weightKg: number;
}

const CUSTOMER_SAMPLES: CustomerSample[] = [
  {
    trackingNumber: 'SD2508240012',
    city: 'Colombo',
    district: 'Western',
    status: 'IN_TRANSIT',
    fee: 750,
    bookedHoursAgo: 4,
    recipient: 'Hassan Ali',
    street: '45, Galle Road',
    postalCode: '00300',
    phone: '+94 76 987 6543',
    parcelType: 'PARCEL',
    contents: 'Books',
    weightKg: 2.5,
  },
  {
    trackingNumber: 'SD2508220098',
    city: 'Kandy',
    district: 'Central',
    status: 'OUT_FOR_DELIVERY',
    fee: 620,
    bookedHoursAgo: 50,
    recipient: 'Nimal Perera',
    street: '12, Peradeniya Road',
    postalCode: '20000',
    phone: '+94 71 234 5678',
    parcelType: 'ELECTRONICS',
    contents: 'Headphones',
    weightKg: 1.1,
  },
  {
    trackingNumber: 'SD2508200045',
    city: 'Galle',
    district: 'Southern',
    status: 'DELIVERED',
    fee: 480,
    bookedHoursAgo: 100,
    recipient: 'Kamala Silva',
    street: '8, Church Street',
    postalCode: '80000',
    phone: '+94 77 345 6789',
    parcelType: 'DOCUMENT',
    contents: 'Contract papers',
    weightKg: 0.4,
  },
  {
    trackingNumber: 'SD2508180176',
    city: 'Jaffna',
    district: 'Northern',
    status: 'DELIVERED',
    fee: 520,
    bookedHoursAgo: 150,
    recipient: 'Suresh Kumar',
    street: '21, Hospital Road',
    postalCode: '40000',
    phone: '+94 76 456 7890',
    parcelType: 'PARCEL',
    contents: 'Clothing',
    weightKg: 1.8,
  },
  {
    trackingNumber: 'SD2508150021',
    city: 'Matara',
    district: 'Southern',
    status: 'FAILED',
    fee: 690,
    bookedHoursAgo: 220,
    recipient: 'Dilani Fernando',
    street: '3, Beach Road',
    postalCode: '81000',
    phone: '+94 70 567 8901',
    parcelType: 'FRAGILE',
    contents: 'Glassware',
    weightKg: 3.2,
  },
  {
    trackingNumber: 'SD2508120077',
    city: 'Negombo',
    district: 'Western',
    status: 'DELIVERED',
    fee: 580,
    bookedHoursAgo: 290,
    recipient: 'Ruwan Jayasuriya',
    street: '17, Lewis Place',
    postalCode: '11500',
    phone: '+94 77 678 9012',
    parcelType: 'PARCEL',
    contents: 'Shoes',
    weightKg: 1.5,
  },
  {
    trackingNumber: 'SD2508100033',
    city: 'Anuradhapura',
    district: 'North Central',
    status: 'DELIVERED',
    fee: 610,
    bookedHoursAgo: 340,
    recipient: 'Chamari Bandara',
    street: '5, Main Street',
    postalCode: '50000',
    phone: '+94 71 789 0123',
    parcelType: 'PERISHABLE',
    contents: 'Homemade sweets',
    weightKg: 2.0,
  },
  {
    trackingNumber: 'SD2508080199',
    city: 'Trincomalee',
    district: 'Eastern',
    status: 'DELIVERED',
    fee: 560,
    bookedHoursAgo: 390,
    recipient: 'Faizal Hameed',
    street: '9, Dockyard Road',
    postalCode: '31000',
    phone: '+94 76 890 1234',
    parcelType: 'PARCEL',
    contents: 'Toys',
    weightKg: 1.3,
  },
];

function orderReference(sample: CustomerSample): string {
  const booked = new Date(hoursAgo(sample.bookedHoursAgo));
  const ymd = booked.toISOString().slice(0, 10).replaceAll('-', '');
  return `ORD-${ymd}-${sample.trackingNumber.slice(-4)}`;
}

function customerDetail(sample: CustomerSample, index: number): CustomerParcelDetail {
  const history = historyUpTo(sample.status, sample.bookedHoursAgo, sample.city);
  return {
    id: `c0000000-0000-4000-8000-00000000000${index + 1}`,
    trackingNumber: sample.trackingNumber,
    orderReference: orderReference(sample),
    bookedAt: history[0].changedAt,
    lastUpdatedAt: history[history.length - 1].changedAt,
    status: sample.status,
    pickupAddress: {
      contactName: 'Ayesha Khan',
      contactPhone: '+94 77 123 4567',
      addressLine1: '123, Flower Road',
      city: 'Colombo 07',
      district: 'Western',
      postalCode: '00700',
    },
    dropoffAddress: {
      contactName: sample.recipient,
      contactPhone: sample.phone,
      addressLine1: sample.street,
      city: sample.city,
      district: sample.district,
      postalCode: sample.postalCode,
    },
    parcelType: sample.parcelType,
    contentDescription: sample.contents,
    weightKg: sample.weightKg,
    lengthCm: 30,
    widthCm: 20,
    heightCm: 15,
    deliveryInstructions: 'Handle with care.',
    quotedFee: sample.fee,
    finalFee: sample.fee,
    feeCurrency: 'LKR',
    paymentMethod: 'Online Payment',
    paymentStatus: 'PAID',
    history,
  };
}

function customerParcels(): CustomerParcelDetail[] {
  return CUSTOMER_SAMPLES.map(customerDetail);
}

function toSummary(parcel: CustomerParcelDetail): CustomerParcelSummary {
  return {
    id: parcel.id,
    trackingNumber: parcel.trackingNumber,
    orderReference: parcel.orderReference,
    destinationCity: parcel.dropoffAddress.city,
    destinationDistrict: parcel.dropoffAddress.district,
    bookedAt: parcel.bookedAt,
    status: parcel.status,
    quotedFee: parcel.quotedFee,
    feeCurrency: parcel.feeCurrency,
  };
}

function countByStatus(statuses: ParcelStatusCode[]): StatusCounts {
  const counts: StatusCounts = {
    CREATED: 0,
    PICKED_UP: 0,
    IN_TRANSIT: 0,
    OUT_FOR_DELIVERY: 0,
    DELIVERED: 0,
    FAILED_ATTEMPT: 0,
    FAILED: 0,
  };
  for (const status of statuses) counts[status] += 1;
  return counts;
}

function receiptText(parcel: CustomerParcelDetail): string {
  return [
    'SwiftDrop - Delivery Receipt (sample)',
    '',
    `Tracking number: ${parcel.trackingNumber}`,
    `Order reference: ${parcel.orderReference ?? '-'}`,
    `Booked on:       ${parcel.bookedAt}`,
    `From:            ${parcel.pickupAddress.city}`,
    `To:              ${parcel.dropoffAddress.city}`,
    `Fee:             ${parcel.feeCurrency} ${parcel.quotedFee.toFixed(2)}`,
    '',
    'The real PDF receipt comes from the backend once the receipt endpoint is built.',
  ].join('\n');
}

// ---- Admin ---------------------------------------------------------------------------------

interface AdminSample {
  trackingNumber: string;
  merchant: string | null;
  recipient: string;
  destination: string;
  district: string;
  weightKg: number;
  status: ParcelStatusCode;
  dispatchState?: DispatchState;
  createdHoursAgo: number;
}

const ADMIN_DESIGN_ROWS: AdminSample[] = [
  {
    trackingNumber: 'SD123456789',
    merchant: 'BrightMart',
    recipient: 'Kasun Perera',
    destination: 'Colombo 07',
    district: 'Colombo',
    weightKg: 2.5,
    status: 'CREATED',
    dispatchState: 'UNASSIGNED',
    createdHoursAgo: 1,
  },
  {
    trackingNumber: 'SD987654321',
    merchant: 'TechZone',
    recipient: 'Nimali Fernando',
    destination: 'Dehiwala',
    district: 'Colombo',
    weightKg: 1.2,
    status: 'CREATED',
    dispatchState: 'ASSIGNED',
    createdHoursAgo: 1.5,
  },
  {
    trackingNumber: 'SD456789123',
    merchant: 'FashionHub',
    recipient: 'Dinesh Silva',
    destination: 'Mount Lavinia',
    district: 'Colombo',
    weightKg: 3.0,
    status: 'IN_TRANSIT',
    createdHoursAgo: 2,
  },
  {
    trackingNumber: 'SD321654987',
    merchant: 'HomeStyle',
    recipient: 'Thilini Rathnayake',
    destination: 'Nugegoda',
    district: 'Colombo',
    weightKg: 0.8,
    status: 'DELIVERED',
    createdHoursAgo: 2.5,
  },
  {
    trackingNumber: 'SD789123456',
    merchant: 'BookWorld',
    recipient: 'Sameera Jayasinghe',
    destination: 'Colombo 05',
    district: 'Colombo',
    weightKg: 1.6,
    status: 'OUT_FOR_DELIVERY',
    createdHoursAgo: 3,
  },
  {
    trackingNumber: 'SD112233445',
    merchant: 'GadgetPlus',
    recipient: 'Anushka Fernando',
    destination: 'Rajagiriya',
    district: 'Colombo',
    weightKg: 2.0,
    status: 'CREATED',
    dispatchState: 'UNASSIGNED',
    createdHoursAgo: 3.5,
  },
  {
    trackingNumber: 'SD998877665',
    merchant: 'StyleHub',
    recipient: 'Lahiru Madushan',
    destination: 'Kottawa',
    district: 'Colombo',
    weightKg: 1.4,
    status: 'FAILED_ATTEMPT',
    createdHoursAgo: 4,
  },
  {
    trackingNumber: 'SD667788990',
    merchant: 'DailyNeeds',
    recipient: 'Samanthi Perera',
    destination: 'Moratuwa',
    district: 'Colombo',
    weightKg: 0.9,
    status: 'CREATED',
    dispatchState: 'UNASSIGNED',
    createdHoursAgo: 4.5,
  },
  {
    trackingNumber: 'SD334455667',
    merchant: 'TechZone',
    recipient: 'Ramesh Perera',
    destination: 'Nugegoda',
    district: 'Colombo',
    weightKg: 2.8,
    status: 'CREATED',
    dispatchState: 'ASSIGNED',
    createdHoursAgo: 5,
  },
  {
    trackingNumber: 'SD445566778',
    merchant: 'BrightMart',
    recipient: 'Nuwan Fernando',
    destination: 'Colombo 03',
    district: 'Colombo',
    weightKg: 1.1,
    status: 'IN_TRANSIT',
    createdHoursAgo: 5.5,
  },
];

const MORE_RECIPIENTS = [
  'Chathura Wijesinghe',
  'Ishara Gunawardena',
  'Pradeep Kumara',
  'Sanduni Herath',
  'Malith Senanayake',
  'Ayoma Dissanayake',
  'Kavindu Rajapaksha',
  'Hiruni Wickramasinghe',
];
const MORE_PLACES: [string, string][] = [
  ['Kandy', 'Kandy'],
  ['Galle', 'Galle'],
  ['Negombo', 'Gampaha'],
  ['Kurunegala', 'Kurunegala'],
  ['Matara', 'Matara'],
  ['Ratnapura', 'Ratnapura'],
];
const MORE_MERCHANTS: (string | null)[] = [
  'BrightMart',
  'TechZone',
  null,
  'FashionHub',
  'HomeStyle',
  null,
  'BookWorld',
  'GadgetPlus',
];
const MORE_STATUSES: ParcelStatusCode[] = [
  'DELIVERED',
  'IN_TRANSIT',
  'DELIVERED',
  'PICKED_UP',
  'CREATED',
  'DELIVERED',
  'OUT_FOR_DELIVERY',
  'FAILED',
  'DELIVERED',
  'CREATED',
];

function adminSamples(): AdminSample[] {
  const more = Array.from({ length: 22 }, (_, i): AdminSample => {
    const status = MORE_STATUSES[i % MORE_STATUSES.length];
    const [destination, district] = MORE_PLACES[i % MORE_PLACES.length];
    return {
      trackingNumber: `SD5${String(10_000_000 + i * 7_919).padStart(8, '0')}`,
      merchant: MORE_MERCHANTS[i % MORE_MERCHANTS.length],
      recipient: MORE_RECIPIENTS[i % MORE_RECIPIENTS.length],
      destination,
      district,
      weightKg: Math.round((0.5 + ((i * 37) % 40) / 10) * 10) / 10,
      status,
      dispatchState: status === 'CREATED' ? (i % 2 === 0 ? 'UNASSIGNED' : 'ASSIGNED') : undefined,
      createdHoursAgo: 6 + i * 3,
    };
  });
  return [...ADMIN_DESIGN_ROWS, ...more];
}

function adminDetail(sample: AdminSample, index: number): AdminParcelDetail {
  const createdAt = hoursAgo(sample.createdHoursAgo);
  return {
    id: `a0000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
    trackingNumber: sample.trackingNumber,
    merchantName: sample.merchant,
    recipientName: sample.recipient,
    destination: sample.destination,
    weightKg: sample.weightKg,
    status: sample.status,
    dispatchState: sample.dispatchState ?? null,
    createdAt,
    serviceType: 'Standard Delivery',
    parcelType: 'PARCEL',
    contentDescription: 'Clothing',
    itemCount: 1,
    paymentMethod: 'Prepaid',
    recipientPhone: '+94 77 123 4567',
    recipientEmail: `${sample.recipient.split(' ')[0].toLowerCase()}.${sample.recipient.split(' ')[1]?.toLowerCase() ?? 'customer'}@gmail.com`,
    dropoffAddress: {
      contactName: sample.recipient,
      contactPhone: '+94 77 123 4567',
      addressLine1: 'No. 15, Park Road',
      city: sample.destination,
      district: `${sample.district === 'Colombo' ? 'Western' : sample.district} Province`,
    },
    deliveryInstructions: 'Call before arrival. Leave at the reception if not available.',
    history: historyUpTo(sample.status, sample.createdHoursAgo, sample.destination),
    notes:
      index === 0
        ? [
            {
              id: 'n1',
              author: 'Admin',
              text: 'Merchant asked for delivery before 5 PM.',
              createdAt,
            },
          ]
        : [],
  };
}

/** Dispatch changes made through the mock dispatch API, by parcel id (CREATED parcels only). */
const dispatchOverrides = new Map<string, DispatchState>();

export function setMockDispatchState(parcelId: string, state: DispatchState): void {
  dispatchOverrides.set(parcelId, state);
}

export function resetMockDispatchStates(): void {
  dispatchOverrides.clear();
}

function adminParcels(): AdminParcelDetail[] {
  return adminSamples()
    .map(adminDetail)
    .map((p) =>
      p.status === 'CREATED' && dispatchOverrides.has(p.id)
        ? { ...p, dispatchState: dispatchOverrides.get(p.id) }
        : p,
    );
}

function inTab(parcel: AdminParcelDetail, tab: AdminParcelTab): boolean {
  switch (tab) {
    case 'ALL':
      return true;
    case 'UNASSIGNED':
    case 'ASSIGNED':
      return parcel.status === 'CREATED' && parcel.dispatchState === tab;
    case 'IN_TRANSIT':
      return ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(parcel.status);
    case 'DELIVERED':
      return parcel.status === tab;
    case 'FAILED':
      // Failed attempts (waiting for the dispatcher) and failed deliveries, like the backend.
      return parcel.status === 'FAILED_ATTEMPT' || parcel.status === 'FAILED';
  }
}

const ADMIN_TABS: AdminParcelTab[] = [
  'ALL',
  'UNASSIGNED',
  'ASSIGNED',
  'IN_TRANSIT',
  'DELIVERED',
  'FAILED',
];

function bookedNewestFirst<T extends { bookedAt: string }>(a: T, b: T): number {
  return b.bookedAt.localeCompare(a.bookedAt);
}

export const mockParcelsApi: ParcelsApi = {
  async getMyCounts() {
    await delay(300);
    return countByStatus(customerParcels().map((parcel) => parcel.status));
  },

  async listMyParcels(filters: CustomerParcelFilters) {
    await delay(400);
    const rows = customerParcels()
      .map(toSummary)
      .filter(
        (row) =>
          matches(filters.search, row.trackingNumber, row.destinationCity, row.orderReference) &&
          (!filters.status || row.status === filters.status) &&
          inDateRange(row.bookedAt, filters.bookedFrom, filters.bookedTo),
      )
      .sort(bookedNewestFirst);
    return page(rows, filters.page, filters.size);
  },

  async getMyParcel(trackingNumber) {
    await delay(400);
    const wanted = trackingNumber.trim().toUpperCase();
    return customerParcels().find((parcel) => parcel.trackingNumber === wanted) ?? null;
  },

  async downloadReceipt(parcelId) {
    await delay(300);
    const parcel = customerParcels().find((p) => p.id === parcelId);
    return new Blob([parcel ? receiptText(parcel) : ''], { type: 'text/plain' });
  },

  async getAdminCounts() {
    await delay(300);
    const all = adminParcels();
    return Object.fromEntries(
      ADMIN_TABS.map((tab) => [tab, all.filter((parcel) => inTab(parcel, tab)).length]),
    ) as AdminParcelCounts;
  },

  async listAdminParcels(filters: AdminParcelFilters) {
    await delay(400);
    const rows = adminParcels().filter(
      (parcel) =>
        inTab(parcel, filters.tab ?? 'ALL') &&
        matches(filters.search, parcel.trackingNumber, parcel.recipientName, parcel.merchantName) &&
        (!filters.status || parcel.status === filters.status) &&
        (!filters.area || parcel.destination === filters.area) &&
        inDateRange(parcel.createdAt, filters.createdFrom, filters.createdTo),
    );
    return page(rows, filters.page, filters.size);
  },

  async getAdminParcel(parcelId) {
    await delay(300);
    return adminParcels().find((parcel) => parcel.id === parcelId) ?? null;
  },
};

/** Destinations used by the admin "Service Area" filter in mock mode. */
export const MOCK_ADMIN_AREAS: string[] = [
  ...new Set(adminSamples().map((sample) => sample.destination)),
].sort();
