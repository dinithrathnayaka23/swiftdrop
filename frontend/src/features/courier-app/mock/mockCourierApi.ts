import { ApiRequestError } from '../../../api/errors';
import { delay } from '../../../api/mock/delay';
import type { ParcelType } from '../../../types/parcel';
import type { ParcelStatusCode } from '../../../types/tracking';
import type {
  CourierApi,
  CourierDelivery,
  CourierDeliveryDetail,
  CourierStop,
  CourierTimelineEntry,
  FailureReason,
} from '../types';

/**
 * Sample data for the courier app (VITE_USE_MOCK_API, and every Jest test). The real rules live in
 * the backend (ParcelStatusCode.nextStatuses); this copy only makes the samples look right.
 */
const NEXT: Record<ParcelStatusCode, ParcelStatusCode[]> = {
  CREATED: ['PICKED_UP'],
  PICKED_UP: ['IN_TRANSIT', 'FAILED'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
  DELIVERED: [],
  FAILED: [],
};

const FLOW: ParcelStatusCode[] = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'];

interface Sample {
  parcelId: string;
  trackingNumber: string;
  status: ParcelStatusCode;
  bookedHoursAgo: number;
  dropoff: CourierStop;
  pickup: CourierStop;
  parcelType: ParcelType;
  weightKg: number;
  contentDescription: string | null;
  deliveryInstructions: string | null;
  /** Set once the courier changes the status here; until then the history is made up from the status. */
  history?: CourierTimelineEntry[];
}

function stop(
  contactName: string,
  contactPhone: string,
  addressLine1: string,
  city: string,
  district: string,
): CourierStop {
  return {
    contactName,
    contactPhone,
    addressLine1,
    addressLine2: null,
    city,
    district,
    postalCode: null,
  };
}

const WAREHOUSE = stop(
  'SwiftDrop Hub',
  '+94112345678',
  '45 Baseline Road',
  'Colombo 09',
  'Colombo',
);

function seed(): Sample[] {
  return [
    {
      parcelId: '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01',
      trackingNumber: 'SD2611120001',
      status: 'OUT_FOR_DELIVERY',
      bookedHoursAgo: 26,
      dropoff: stop('Kasun Perera', '+94771234567', '12 Galle Road', 'Colombo 03', 'Colombo'),
      pickup: WAREHOUSE,
      parcelType: 'PARCEL',
      weightKg: 2.5,
      contentDescription: 'Books',
      deliveryInstructions: 'Call on arrival. Leave with the security guard if not home.',
    },
    {
      parcelId: '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e02',
      trackingNumber: 'SD2611120002',
      status: 'IN_TRANSIT',
      bookedHoursAgo: 20,
      dropoff: stop('Nadeesha Fernando', '+94712223344', '8 Lake Drive', 'Rajagiriya', 'Colombo'),
      pickup: WAREHOUSE,
      parcelType: 'FRAGILE',
      weightKg: 1.2,
      contentDescription: 'Ceramic vase',
      deliveryInstructions: 'Fragile: handle with care.',
    },
    {
      parcelId: '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e03',
      trackingNumber: 'SD2611120003',
      status: 'PICKED_UP',
      bookedHoursAgo: 8,
      dropoff: stop('Mohamed Rizwan', '+94756667788', '21 Station Road', 'Dehiwala', 'Colombo'),
      pickup: stop('Tech World', '+94114455667', '101 Duplication Road', 'Colombo 04', 'Colombo'),
      parcelType: 'ELECTRONICS',
      weightKg: 0.8,
      contentDescription: 'Mobile phone',
      deliveryInstructions: null,
    },
    {
      parcelId: '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e04',
      trackingNumber: 'SD2611120004',
      status: 'CREATED',
      bookedHoursAgo: 3,
      dropoff: stop('Ishara Jayasinghe', '+94701112233', '3 Temple Lane', 'Nugegoda', 'Colombo'),
      pickup: stop('Nimal Silva', '+94779998877', '5 Flower Road', 'Colombo 07', 'Colombo'),
      parcelType: 'DOCUMENT',
      weightKg: 0.2,
      contentDescription: 'Signed contract',
      deliveryInstructions: 'Hand to the recipient only.',
    },
    {
      parcelId: '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e05',
      trackingNumber: 'SD2611120005',
      status: 'CREATED',
      bookedHoursAgo: 1,
      dropoff: stop(
        'Ruwani Wickramasinghe',
        '+94768889900',
        '77 Kandy Road',
        'Kiribathgoda',
        'Gampaha',
      ),
      pickup: stop('Fresh Bakes', '+94115556677', '14 Park Street', 'Colombo 02', 'Colombo'),
      parcelType: 'PERISHABLE',
      weightKg: 3,
      contentDescription: 'Birthday cake',
      deliveryInstructions: 'Keep upright.',
    },
  ];
}

let samples: Sample[] = seed();

/** Puts the sample deliveries back as they were (for tests). */
export function resetMockCourierData(): void {
  samples = seed();
}

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString();
}

function timeline(sample: Sample): CourierTimelineEntry[] {
  if (sample.history) return sample.history;
  const steps = FLOW.includes(sample.status)
    ? FLOW.slice(0, FLOW.indexOf(sample.status) + 1)
    : [...FLOW, sample.status];
  const gap = sample.bookedHoursAgo / (steps.length + 1);
  return steps.map((status, i) => ({
    status,
    changedAt: hoursAgo(sample.bookedHoursAgo - i * gap),
    note: null,
    failureReason: null,
  }));
}

function toItem(sample: Sample): CourierDelivery {
  return {
    parcelId: sample.parcelId,
    trackingNumber: sample.trackingNumber,
    dropoff: sample.dropoff,
    pickup: sample.status === 'CREATED' ? sample.pickup : null,
    parcelType: sample.parcelType,
    weightKg: sample.weightKg,
    deliveryInstructions: sample.deliveryInstructions,
    currentStatus: sample.status,
    allowedActions: NEXT[sample.status],
  };
}

function toDetail(sample: Sample): CourierDeliveryDetail {
  return {
    ...toItem(sample),
    pickup: sample.pickup,
    lengthCm: null,
    widthCm: null,
    heightCm: null,
    contentDescription: sample.contentDescription,
    bookedAt: hoursAgo(sample.bookedHoursAgo),
    timeline: timeline(sample),
  };
}

const FAILURE_REASONS: FailureReason[] = [
  { code: 'RECIPIENT_UNAVAILABLE', label: 'Recipient unavailable', noteRequired: false },
  { code: 'WRONG_ADDRESS', label: 'Wrong address', noteRequired: false },
  { code: 'REFUSED', label: 'Refused by recipient', noteRequired: false },
  { code: 'ACCESS_ISSUE', label: 'Access issue', noteRequired: false },
  { code: 'DAMAGED', label: 'Parcel damaged', noteRequired: true },
  { code: 'OTHER', label: 'Other', noteRequired: true },
];

/**
 * The sample, while it is still the courier's. As in the backend, a delivered or failed parcel's
 * assignment ends, so it leaves Today's Deliveries and its details are no longer found.
 */
function openSample(parcelId: string): Sample | undefined {
  return samples.find((s) => s.parcelId === parcelId && NEXT[s.status].length > 0);
}

function notFound(): ApiRequestError {
  return new ApiRequestError('Delivery not found', { status: 404, code: 'NOT_FOUND' });
}

function conflict(from: ParcelStatusCode, to: ParcelStatusCode): ApiRequestError {
  return new ApiRequestError(`A parcel that is ${from} cannot be changed to ${to}`, {
    status: 409,
    code: 'CONFLICT',
  });
}

export const mockCourierApi: CourierApi = {
  async getToday() {
    await delay(300);
    const deliveries = samples.filter((s) => NEXT[s.status].length > 0).map(toItem);
    return {
      counts: {
        total: deliveries.length,
        assigned: deliveries.filter((d) => d.currentStatus === 'CREATED').length,
        inProgress: deliveries.filter(
          (d) => d.currentStatus !== 'CREATED' && d.allowedActions.length > 0,
        ).length,
      },
      deliveries,
    };
  },

  async getDelivery(parcelId) {
    await delay(300);
    const sample = openSample(parcelId);
    return sample ? toDetail(sample) : null;
  },

  async findByTrackingNumber(trackingNumber) {
    await delay(300);
    const wanted = trackingNumber.replace(/\s+/g, '').toUpperCase();
    const sample = samples.find((s) => s.trackingNumber === wanted);
    return sample && openSample(sample.parcelId) ? toDetail(sample) : null;
  },

  async updateStatus(parcelId, { status, note }) {
    await delay(300);
    const sample = openSample(parcelId);
    if (!sample) throw notFound();
    if (!NEXT[sample.status].includes(status)) throw conflict(sample.status, status);
    sample.history = [
      ...timeline(sample),
      {
        status,
        changedAt: new Date().toISOString(),
        note: note?.trim() || null,
        failureReason: null,
      },
    ];
    sample.status = status;
    return toDetail(sample);
  },

  async getFailureReasons() {
    await delay(200);
    return FAILURE_REASONS.map((reason) => ({ ...reason }));
  },

  async reportFailure(parcelId, { reasonCode, note }) {
    await delay(300);
    const reason = FAILURE_REASONS.find((r) => r.code === reasonCode);
    if (!reason) {
      throw new ApiRequestError('Choose one of the failure reasons', {
        status: 400,
        code: 'BAD_REQUEST',
      });
    }
    const trimmed = note?.trim() || null;
    if (reason.noteRequired && !trimmed) {
      throw new ApiRequestError('Add a note to explain this reason', {
        status: 400,
        code: 'BAD_REQUEST',
      });
    }
    const sample = openSample(parcelId);
    if (!sample) throw notFound();
    if (!NEXT[sample.status].includes('FAILED')) throw conflict(sample.status, 'FAILED');
    sample.history = [
      ...timeline(sample),
      {
        status: 'FAILED',
        changedAt: new Date().toISOString(),
        note: null,
        failureReason: trimmed ? `${reason.label}: ${trimmed}` : reason.label,
      },
    ];
    sample.status = 'FAILED';
    return toDetail(sample);
  },
};
