import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { trackingApi } from '../../../api/tracking';
import type { TrackingResult } from '../../../types/tracking';
import { useLiveUpdates } from '../../shared-live/useLiveUpdates';
import TrackingPage from './TrackingPage';

jest.mock('../../../utils/env', () => ({
  ...jest.requireActual('../../../utils/env'),
  isMockApiEnabled: () => false,
}));
jest.mock('../../../api/tracking', () => ({ trackingApi: { getTracking: jest.fn() } }));
jest.mock('../../shared-live/useLiveUpdates', () => ({ useLiveUpdates: jest.fn() }));

const live = jest.mocked(useLiveUpdates);

const currentStatus = () =>
  screen.getByRole('heading', { name: 'Current Status' }).closest('section')!;

const RESULT: TrackingResult = {
  trackingNumber: 'SD123456789',
  currentStatus: 'PICKED_UP',
  lastUpdatedAt: '2026-11-07T05:00:00Z',
  bookedAt: '2026-11-07T04:00:00Z',
  fromCity: 'Kandy',
  toCity: 'Colombo',
  weightKg: 2.5,
  history: [
    { statusCode: 'CREATED', changedAt: '2026-11-07T04:00:00Z' },
    { statusCode: 'PICKED_UP', changedAt: '2026-11-07T05:00:00Z' },
  ],
};

it('shows the Live badge and applies newer status changes from the stream', async () => {
  live.mockReturnValue('live');
  jest.mocked(trackingApi.getTracking).mockResolvedValue(RESULT);
  render(
    <MemoryRouter initialEntries={['/track/SD123456789']}>
      <Routes>
        <Route path="/track/:trackingNumber" element={<TrackingPage />} />
      </Routes>
    </MemoryRouter>,
  );

  await screen.findByRole('heading', { name: '#SD123456789' });
  expect(screen.getByRole('status')).toHaveTextContent('Live');
  const options = live.mock.calls[live.mock.calls.length - 1][0];
  expect(options.path).toBe('/public/tracking/SD123456789/stream');

  // An old update (version 2 is already shown) is ignored; a newer one moves the parcel on.
  const update = { trackingNumber: 'SD123456789', changedAt: '2026-11-07T06:00:00Z' };
  act(() =>
    options.onUpdate({
      ...update,
      previousStatus: 'CREATED',
      status: 'PICKED_UP',
      statusVersion: 2,
    }),
  );
  expect(currentStatus()).toHaveTextContent('Picked Up');
  act(() =>
    options.onUpdate({
      ...update,
      previousStatus: 'PICKED_UP',
      status: 'IN_TRANSIT',
      statusVersion: 3,
    }),
  );
  expect(currentStatus()).toHaveTextContent('In Transit');
});
