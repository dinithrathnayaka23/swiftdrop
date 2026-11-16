import { ApiRequestError } from '../../../api/errors';
import { delay } from '../../../api/mock/delay';
import type { ParcelType } from '../../../types/parcel';
import type { ParcelStatusCode } from '../../../types/tracking';
import type {
  CompletedDelivery,
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
  PICKED_UP: ['IN_TRANSIT', 'FAILED_ATTEMPT'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY', 'FAILED_ATTEMPT'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED_ATTEMPT'],
  DELIVERED: [],
  // Retrying (CREATED) or closing (FAILED) a failed attempt is the dispatcher's, not the courier's.
  FAILED_ATTEMPT: [],
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

/** Earlier finished deliveries for Completed Deliveries; delivering or failing here adds more. */
function seedCompleted(): CompletedDelivery[] {
  const row = (
    hours: number,
    trackingNumber: string,
    recipientName: string,
    city: string,
    outcome: CompletedDelivery['outcome'],
    extra: Partial<CompletedDelivery>,
  ): CompletedDelivery => ({
    parcelId: `9c1d2e3f-0000-4000-8000-${trackingNumber.slice(-12).padStart(12, '0')}`,
    trackingNumber,
    outcome,
    completedAt: hoursAgo(hours),
    recipientName,
    dropoffArea: '10 Main Street',
    dropoffCity: city,
    receivedBy: null,
    failureReason: null,
    note: null,
    proofPhotoUrl: null,
    ...extra,
  });
  return [
    row(0, 'SD2611140007', 'Dilan Rajapaksa', 'Colombo 05', 'DELIVERED', {
      receivedBy: 'Dilan Rajapaksa',
      proofPhotoUrl: '/proofs/mock-proof-1/photo',
    }),
    row(5, 'SD2611140006', 'Sanduni Herath', 'Maharagama', 'FAILED_ATTEMPT', {
      failureReason: 'Recipient unavailable',
      note: 'Called twice, no answer',
    }),
    row(27, 'SD2611130004', 'Tharindu Silva', 'Kotte', 'DELIVERED', {
      receivedBy: 'Security guard',
    }),
  ];
}

let samples: Sample[] = seed();
let completed: CompletedDelivery[] = seedCompleted();

/** Puts the sample deliveries back as they were (for tests). */
export function resetMockCourierData(): void {
  samples = seed();
  completed = seedCompleted();
}

/** Records a finished delivery for Completed Deliveries (newest first). */
function complete(
  sample: Sample,
  outcome: CompletedDelivery['outcome'],
  extra: Partial<CompletedDelivery>,
) {
  completed.unshift({
    parcelId: sample.parcelId,
    trackingNumber: sample.trackingNumber,
    outcome,
    completedAt: new Date().toISOString(),
    recipientName: sample.dropoff.contactName,
    dropoffArea: sample.dropoff.addressLine1,
    dropoffCity: sample.dropoff.city,
    receivedBy: null,
    failureReason: null,
    note: null,
    proofPhotoUrl: null,
    ...extra,
  });
}

/** yyyy-MM-dd of an ISO timestamp, in the browser's time zone (good enough for samples). */
function localDay(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** A stand-in proof photo (the mock has no files). */
const MOCK_PHOTO_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="#e2e8f0"/><text x="200" y="155" font-family="sans-serif" font-size="22" text-anchor="middle" fill="#475569">Proof photo (sample)</text></svg>';

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
 * The sample, while it is still the courier's. As in the backend, a delivered parcel's or a failed
 * attempt's assignment ends, so it leaves Today's Deliveries and its details are no longer found.
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
        completedToday: completed.filter(
          (c) =>
            c.outcome === 'DELIVERED' &&
            localDay(c.completedAt) === localDay(new Date().toISOString()),
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
    if (!NEXT[sample.status].includes('FAILED_ATTEMPT')) {
      throw conflict(sample.status, 'FAILED_ATTEMPT');
    }
    sample.history = [
      ...timeline(sample),
      {
        status: 'FAILED_ATTEMPT',
        changedAt: new Date().toISOString(),
        note: null,
        failureReason: trimmed ? `${reason.label}: ${trimmed}` : reason.label,
      },
    ];
    // The dispatcher retries or closes it; for the courier the delivery is over (it leaves the list).
    sample.status = 'FAILED_ATTEMPT';
    complete(sample, 'FAILED_ATTEMPT', { failureReason: reason.label, note: trimmed });
    return toDetail(sample);
  },

  async deliver(parcelId, { recipientName, note, photo }) {
    await delay(photo ? 600 : 300);
    const name = recipientName.trim();
    if (!name) {
      throw new ApiRequestError('Request validation failed', {
        status: 400,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'recipientName', message: "Recipient's name is required" }],
      });
    }
    const sample = openSample(parcelId);
    if (!sample) throw notFound();
    if (!NEXT[sample.status].includes('DELIVERED')) throw conflict(sample.status, 'DELIVERED');
    sample.history = [
      ...timeline(sample),
      {
        status: 'DELIVERED',
        changedAt: new Date().toISOString(),
        note: note?.trim() || null,
        failureReason: null,
      },
    ];
    // Delivered: the assignment ends, so the delivery leaves the courier's list.
    sample.status = 'DELIVERED';
    complete(sample, 'DELIVERED', {
      receivedBy: name,
      proofPhotoUrl: photo ? `/proofs/mock-${sample.parcelId}/photo` : null,
    });
    return toDetail(sample);
  },

  async getCompleted({ outcome, from, to, page, size = 20 }) {
    await delay(300);
    const rows = completed
      .filter((c) => !outcome || c.outcome === outcome)
      .filter((c) => !from || localDay(c.completedAt) >= from)
      .filter((c) => !to || localDay(c.completedAt) <= to)
      .sort((a, b) => b.completedAt.localeCompare(a.completedAt));
    return {
      content: rows.slice(page * size, page * size + size),
      page: {
        size,
        number: page,
        totalElements: rows.length,
        totalPages: Math.ceil(rows.length / size),
      },
    };
  },

  async getProofPhoto(proofPhotoUrl) {
    await delay(300);
    if (!completed.some((c) => c.proofPhotoUrl === proofPhotoUrl)) {
      throw new ApiRequestError('Photo not found', { status: 404, code: 'NOT_FOUND' });
    }
    return new Blob([MOCK_PHOTO_SVG], { type: 'image/svg+xml' });
  },
};
