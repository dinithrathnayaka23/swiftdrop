import { pageItems } from './pagination';
import type { StatusHistoryItem } from '../../types/parcel';
import type { ParcelStatusCode } from '../../types/tracking';
import {
  buildProgress,
  countActive,
  formatFee,
  formatRelative,
  historyText,
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

  it('ends a failed parcel with a current FAILED step', () => {
    const steps = buildProgress(history('CREATED', 'PICKED_UP', 'FAILED'));
    expect(steps.map((s) => [s.code, s.state])).toEqual([
      ['CREATED', 'complete'],
      ['PICKED_UP', 'complete'],
      ['FAILED', 'current'],
    ]);
  });
});

describe('historyText', () => {
  it('prefers the failure reason, then the note, then a default line', () => {
    expect(
      historyText({ status: 'FAILED', changedAt: '', failureReason: 'Recipient not available' }),
    ).toBe('Reason: Recipient not available');
    expect(historyText({ status: 'IN_TRANSIT', changedAt: '', note: 'On the way to Kandy.' })).toBe(
      'On the way to Kandy.',
    );
    expect(historyText({ status: 'CREATED', changedAt: '' })).toBe(
      'Delivery request has been created.',
    );
  });
});

describe('countActive', () => {
  it('counts every status that is not delivered or failed', () => {
    expect(
      countActive({
        CREATED: 1,
        PICKED_UP: 2,
        IN_TRANSIT: 3,
        OUT_FOR_DELIVERY: 4,
        DELIVERED: 9,
        FAILED: 9,
      }),
    ).toBe(10);
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
