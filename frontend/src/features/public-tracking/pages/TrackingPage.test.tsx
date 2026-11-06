import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { MOCK_TRACKING_SAMPLES } from '../../../api/mock/mockTrackingApi';
import { trackingApi, TrackingRateLimitError } from '../../../api/tracking';
import { SettingsContext } from '../../../context/settingsContextValue';
import { PATHS } from '../../../routes/paths';
import { DEFAULT_PUBLIC_SETTINGS } from '../../../types/settings';
import type { ParcelStatusCode, TrackingResult } from '../../../types/tracking';
import TrackingPage from './TrackingPage';

// Only trackingApi is replaced; TrackingRateLimitError stays the real class.
jest.mock('../../../api/tracking', () => ({
  ...jest.requireActual<typeof import('../../../api/tracking')>('../../../api/tracking'),
  trackingApi: { getTracking: jest.fn() },
}));

const getTracking = jest.mocked(trackingApi.getTracking);

const FLOW: ParcelStatusCode[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
];

/** A parcel whose history went through `steps` (one hour apart) and ended in the last one. */
function parcel(...steps: ParcelStatusCode[]): TrackingResult {
  const history = steps.map((statusCode, i) => ({
    statusCode,
    changedAt: new Date(Date.UTC(2026, 10, 1, 4 + i)).toISOString(),
  }));
  return {
    trackingNumber: 'SD123456789',
    currentStatus: steps[steps.length - 1],
    bookedAt: history[0].changedAt,
    lastUpdatedAt: history[history.length - 1].changedAt,
    fromCity: 'Colombo',
    toCity: 'Kandy',
    weightKg: 2.5,
    history,
  };
}

function renderAt(path: string, wrapper?: (children: ReactNode) => ReactNode) {
  const page = (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={PATHS.track} element={<TrackingPage />} />
        <Route path={PATHS.trackResult} element={<TrackingPage />} />
      </Routes>
    </MemoryRouter>
  );
  render(wrapper ? wrapper(page) : page);
}

/**
 * Labels of the timeline steps with this screen-reader state. The timeline is drawn twice (phone
 * and desktop layouts); the first copy is enough.
 */
function stepsMarked(state: string): string[] {
  const [timeline] = screen.getAllByRole('list', { name: 'Delivery progress' });
  return within(timeline)
    .getAllByRole('listitem')
    .map((item) => item.querySelector('p'))
    .filter((label) => label?.textContent?.endsWith(`(${state})`))
    .map((label) => label?.firstChild?.textContent?.trim() ?? '');
}

beforeEach(() => {
  getTracking.mockReset();
});

afterEach(() => {
  jest.useRealTimers();
});

describe('TrackingPage results', () => {
  it('shows a booked parcel as awaiting pickup without any progress', async () => {
    getTracking.mockResolvedValue(parcel('CREATED'));
    renderAt('/track/SD123456789');

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(screen.getByText('Awaiting Pickup')).toBeInTheDocument();
    expect(screen.getByText('Waiting for pickup')).toBeInTheDocument();
    expect(stepsMarked('completed')).toEqual([]);
    expect(stepsMarked('current step')).toEqual([]);
    expect(stepsMarked('not reached')).toHaveLength(4);
  });

  it('shows a parcel in transit with its reached and current steps', async () => {
    getTracking.mockResolvedValue(parcel('CREATED', 'PICKED_UP', 'IN_TRANSIT'));
    renderAt('/track/SD123456789');

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(stepsMarked('completed')).toEqual(['Picked Up']);
    expect(stepsMarked('current step')).toEqual(['In Transit']);
    expect(screen.getByText('Colombo')).toBeInTheDocument();
    expect(screen.getByText('Kandy')).toBeInTheDocument();
    expect(screen.getByText('2.5 kg')).toBeInTheDocument();
    expect(screen.queryByText('Waiting for pickup')).not.toBeInTheDocument();
  });

  it('shows a delivered parcel with every step completed', async () => {
    getTracking.mockResolvedValue(parcel(...FLOW));
    renderAt('/track/SD123456789');

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(stepsMarked('completed')).toEqual([
      'Picked Up',
      'In Transit',
      'Out for Delivery',
      'Delivered',
    ]);
    expect(screen.getByRole('button', { name: 'Share' })).toBeInTheDocument();
  });

  it('stops a failed delivery at the step where it failed and points to support', async () => {
    getTracking.mockResolvedValue(
      parcel('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'FAILED'),
    );
    renderAt('/track/SD123456789');

    expect(await screen.findByText('Delivery Unsuccessful')).toBeInTheDocument();
    expect(stepsMarked('completed')).toEqual(['Picked Up', 'In Transit']);
    expect(stepsMarked('delivery failed at this step')).toEqual(['Out for Delivery']);
    expect(stepsMarked('not reached')).toEqual(['Delivered']);
    expect(screen.getByRole('link', { name: 'support@swiftdrop.lk' })).toHaveAttribute(
      'href',
      'mailto:support@swiftdrop.lk',
    );
    // The public API sends no failure reason, and the page never claims one was recorded.
    expect(screen.queryByText(/reason/i)).not.toBeInTheDocument();
  });

  it('uses the support phone number from the system settings when there is one', async () => {
    getTracking.mockResolvedValue(parcel('CREATED', 'PICKED_UP', 'FAILED'));
    const settings = {
      ...DEFAULT_PUBLIC_SETTINGS,
      contactEmail: 'help@swiftdrop.lk',
      contactPhone: '+94 11 234 5678',
    };
    renderAt('/track/SD123456789', (children) => (
      <SettingsContext.Provider value={{ settings, refresh: async () => undefined }}>
        {children}
      </SettingsContext.Provider>
    ));

    expect(await screen.findByRole('link', { name: '+94 11 234 5678' })).toHaveAttribute(
      'href',
      'tel:+94112345678',
    );
    expect(screen.getByRole('link', { name: 'help@swiftdrop.lk' })).toBeInTheDocument();
    expect(stepsMarked('delivery failed at this step')).toEqual(['Picked Up']);
  });

  it('shows "Parcel Not Found" for an unknown tracking number', async () => {
    getTracking.mockResolvedValue(null);
    renderAt('/track/SD000000000');

    expect(await screen.findByText('Parcel Not Found')).toBeInTheDocument();
    expect(screen.getByText(/SD000000000/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Try Again' })).toHaveAttribute('href', PATHS.track);
  });

  it('shows other errors as "Something Went Wrong" and loads again on retry', async () => {
    getTracking
      .mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'))
      .mockResolvedValueOnce(parcel('CREATED', 'PICKED_UP'));
    renderAt('/track/SD123456789');

    expect(await screen.findByText('Something Went Wrong')).toBeInTheDocument();
    expect(screen.getByText('Unable to reach the server.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Try Again' }));

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(getTracking).toHaveBeenCalledTimes(2);
  });
});

/** One second of the countdown (it re-arms its timer after each render). */
async function tick() {
  await act(async () => {
    jest.advanceTimersByTime(1000);
  });
}

describe('TrackingPage rate limit (429)', () => {
  it('asks the visitor to wait for Retry-After, then lets them try again', async () => {
    jest.useFakeTimers({ advanceTimers: true });
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    getTracking
      .mockRejectedValueOnce(new TrackingRateLimitError('Too many tracking look-ups.', 3))
      .mockResolvedValueOnce(parcel('CREATED', 'PICKED_UP'));
    renderAt('/track/SD123456789');

    expect(await screen.findByText('Too Many Searches')).toBeInTheDocument();
    expect(screen.getByText(/Please try again in 3 seconds\./)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again in 3 seconds' })).toBeDisabled();

    await tick();
    expect(screen.getByRole('button', { name: 'Try again in 2 seconds' })).toBeDisabled();
    await tick();
    expect(screen.getByRole('button', { name: 'Try again in 1 second' })).toBeDisabled();

    await tick();
    const retry = screen.getByRole('button', { name: 'Try Again' });
    expect(retry).toBeEnabled();

    await user.click(retry);

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(getTracking).toHaveBeenCalledTimes(2);
  });

  it('counts longer waits in minutes', async () => {
    getTracking.mockRejectedValue(new TrackingRateLimitError('Too many tracking look-ups.', 90));
    renderAt('/track/SD123456789');

    expect(await screen.findByRole('button', { name: 'Try again in 2 minutes' })).toBeDisabled();
  });
});

describe('TrackingPage search and tracking numbers', () => {
  it('lists every mock sample, linked to its result, while the mock API is on', () => {
    renderAt('/track');

    expect(screen.getByRole('heading', { name: 'Track Your Parcel' })).toBeInTheDocument();
    for (const { trackingNumber, label } of MOCK_TRACKING_SAMPLES) {
      const link = screen.getByRole('link', { name: trackingNumber });
      expect(link).toHaveAttribute('href', `/track/${trackingNumber}`);
      expect(link.parentElement).toHaveTextContent(`(${label})`);
    }
    expect(MOCK_TRACKING_SAMPLES.map((s) => s.label)).toEqual([
      'booked',
      'in transit',
      'delivered',
      'failed',
    ]);
  });

  it('redirects a lower-case number with spaces in the URL to the clean number', async () => {
    getTracking.mockResolvedValue(parcel('CREATED'));
    renderAt('/track/%20sd123456789%20');

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(getTracking).toHaveBeenCalledTimes(1);
    expect(getTracking).toHaveBeenCalledWith('SD123456789');
  });

  it('sends a URL with only spaces back to the search page', () => {
    renderAt('/track/%20%20');

    expect(screen.getByRole('heading', { name: 'Track Your Parcel' })).toBeInTheDocument();
    expect(getTracking).not.toHaveBeenCalled();
  });

  it('searches for the trimmed, upper-case number typed in the search box', async () => {
    getTracking.mockResolvedValue(parcel('CREATED'));
    renderAt('/track');

    await userEvent.type(screen.getByLabelText('Tracking number'), '  sd123456789 ');
    await userEvent.click(screen.getByRole('button', { name: 'Track Parcel' }));

    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(getTracking).toHaveBeenCalledWith('SD123456789');
  });
});
