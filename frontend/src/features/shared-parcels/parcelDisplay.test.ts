import { pageItems } from './pagination';
import type { StatusHistoryItem } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';
import {
  buildProgress,
  countActive,
  formatFee,
  formatRelative,
  historyText,
  latestFailureReason,
} from './parcelDisplay';

function history(...statuses: ParcelStatusCode[]): StatusHistoryItem[] {
  return statuses.map((status, i) => ({ status, changedAt: `2026-01-0${i + 1}T10:00:00Z` }));
}

describe('formatFee', () => {
  it('shows rupees with two decimals', () => {
    expect(formatFee(750)).toBe('Rs. 750.00');
    expect(formatFee(1234.5)).toBe('Rs. 1,234.50');
  });
});

describe('formatRelative', () => {
  const now = Date.parse('2026-01-10T12:00:00Z');
  it.each([
    ['2026-01-10T12:00:00Z', 'just now'],
    ['2026-01-10T11:55:00Z', '5 minutes ago'],
    ['2026-01-10T10:00:00Z', '2h ago'],
    ['2026-01-07T12:00:00Z', '3 days ago'],
  ])('%s is "%s"', (iso, expected) => {
    expect(formatRelative(iso, now)).toBe(expected);
  });
});

describe('buildProgress', () => {
  it('marks reached steps complete and the latest one current', () => {
    const steps = buildProgress(history('CREATED', 'PICKED_UP', 'IN_TRANSIT'));
    expect(steps.map((s) => s.state)).toEqual([
      'complete',
      'complete',
      'current',
      'upcoming',
      'upcoming',
    ]);
  });

  it('marks every step complete once delivered', () => {
    const steps = buildProgress(
      history('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'),
    );
    expect(steps.every((s) => s.state === 'complete')).toBe(true);
  });

  it('ends a failed attempt with a current FAILED_ATTEMPT step', () => {
    const steps = buildProgress(history('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'FAILED_ATTEMPT'));
    expect(steps.map((s) => [s.code, s.state])).toEqual([
      ['CREATED', 'complete'],
      ['PICKED_UP', 'complete'],
      ['IN_TRANSIT', 'complete'],
      ['FAILED_ATTEMPT', 'current'],
    ]);
    expect(steps[3].label).toBe('Delivery attempt failed');
  });

  it('ends a closed failure with a current FAILED step', () => {
    const steps = buildProgress(history('CREATED', 'PICKED_UP', 'FAILED_ATTEMPT', 'FAILED'));
    expect(steps.map((s) => [s.code, s.state])).toEqual([
      ['CREATED', 'complete'],
      ['PICKED_UP', 'complete'],
      ['FAILED', 'current'],
    ]);
  });

  it('starts over for a retried parcel', () => {
    const retried = history(
      'CREATED',
      'PICKED_UP',
      'IN_TRANSIT',
      'OUT_FOR_DELIVERY',
      'FAILED_ATTEMPT',
      'CREATED',
    );
    const steps = buildProgress(retried);
    expect(steps.map((s) => [s.code, s.state])).toEqual([
      ['CREATED', 'current'],
      ['PICKED_UP', 'upcoming'],
      ['IN_TRANSIT', 'upcoming'],
      ['OUT_FOR_DELIVERY', 'upcoming'],
      ['DELIVERED', 'upcoming'],
    ]);
    expect(steps[0].reachedAt).toBe(retried[5].changedAt);

    const delivered = buildProgress([
      ...retried,
      ...history('PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'),
    ]);
    expect(delivered.map((s) => s.code)).not.toContain('FAILED_ATTEMPT');
    expect(delivered.every((s) => s.state === 'complete')).toBe(true);
  });
});

describe('latestFailureReason', () => {
  const failures: StatusHistoryItem[] = [
    { status: 'CREATED', changedAt: '' },
    { status: 'PICKED_UP', changedAt: '' },
    { status: 'FAILED_ATTEMPT', changedAt: '', failureReason: 'Recipient not available' },
    { status: 'CREATED', changedAt: '' },
    { status: 'PICKED_UP', changedAt: '' },
    { status: 'FAILED_ATTEMPT', changedAt: '', failureReason: 'Shop closed' },
  ];

  it('gives the reason of the latest attempt', () => {
    expect(latestFailureReason(failures)).toBe('Shop closed');
    expect(
      latestFailureReason([
        ...failures,
        { status: 'FAILED', changedAt: '', failureReason: 'Address does not exist' },
      ]),
    ).toBe('Address does not exist');
  });

  it('gives nothing once the parcel is retried or still on the way', () => {
    expect(latestFailureReason([...failures, { status: 'CREATED', changedAt: '' }])).toBeNull();
    expect(latestFailureReason(failures.slice(0, 2))).toBeNull();
  });
});

describe('historyText', () => {
  it('prefers the failure reason, then the note, then a default line', () => {
    expect(
      historyText({ status: 'FAILED', changedAt: '', failureReason: 'Recipient not available' }),
    ).toBe('Reason: Recipient not available');
    expect(
      historyText({ status: 'FAILED_ATTEMPT', changedAt: '', failureReason: 'Shop closed' }),
    ).toBe('Reason: Shop closed');
    expect(historyText({ status: 'FAILED_ATTEMPT', changedAt: '' })).toBe(
      'The delivery attempt failed.',
    );
    expect(historyText({ status: 'IN_TRANSIT', changedAt: '', note: 'On the way to Kandy.' })).toBe(
      'On the way to Kandy.',
    );
    expect(historyText({ status: 'CREATED', changedAt: '' })).toBe(
      'Delivery request has been created.',
    );
  });
});

describe('countActive', () => {
  it('counts every status that is not finished, failed attempts included', () => {
    expect(
      countActive({
        CREATED: 1,
        PICKED_UP: 2,
        IN_TRANSIT: 3,
        OUT_FOR_DELIVERY: 4,
        DELIVERED: 9,
        FAILED_ATTEMPT: 5,
        FAILED: 9,
      }),
    ).toBe(15);
  });
});

describe('pageItems', () => {
  it('shows all pages when there are few', () => {
    expect(pageItems(0, 3)).toEqual([0, 1, 2]);
  });

  it('puts a gap between distant pages', () => {
    expect(pageItems(5, 13)).toEqual([0, 'gap', 4, 5, 6, 'gap', 12]);
  });
});
