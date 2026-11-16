import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { dispatchApi } from '../../../api/dispatch';
import { ApiRequestError } from '../../../api/errors';
import { resetMockCouriers } from '../../../api/mock/mockCouriersApi';
import { resetMockFailedDeliveries } from '../../../api/mock/mockDispatchApi';
import { resetMockDispatchStates } from '../../../api/mock/mockParcelsApi';
import { resetMockServiceAreas } from '../../../api/mock/mockServiceAreasApi';
import OperationsOverviewPage from './OperationsOverviewPage';

// The mock API answers after short delays; several steps per test.
jest.setTimeout(20_000);

const FAILED_ATTEMPT_ID = 'a0000000-0000-4000-8000-000000000007'; // SD998877665

function renderPage() {
  render(
    <MemoryRouter>
      <OperationsOverviewPage />
    </MemoryRouter>,
  );
}

function region(name: string): HTMLElement {
  return screen.getByRole('region', { name });
}

/** The KPI card (a link) whose accessible name starts with the label. */
async function card(label: string): Promise<HTMLElement> {
  return within(region("Today's numbers")).findByRole('link', { name: new RegExp(`^${label}`) });
}

function setHidden(hidden: boolean, notify = true) {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
  if (notify) document.dispatchEvent(new Event('visibilitychange'));
}

describe('OperationsOverviewPage (mock API)', () => {
  beforeEach(() => {
    resetMockServiceAreas();
    resetMockCouriers();
    resetMockDispatchStates();
    resetMockFailedDeliveries();
  });
  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
    setHidden(false, false);
  });

  it('shows numbers that agree with the blocks and the other screens', async () => {
    renderPage();
    // 5 waiting parcels in the sample data, all listed in the Unassigned block.
    await waitFor(async () => expect(await card('Unassigned Parcels')).toHaveTextContent('5'));
    const unassigned = region('Unassigned Parcels');
    expect(await within(unassigned).findAllByRole('link', { name: /^Assign #SD/ })).toHaveLength(5);

    // Couriers summary: 8 of 11 active couriers online, Kasun on a break.
    expect(await card('Available Couriers')).toHaveTextContent('8 / 11');
    expect(await card('Available Couriers')).toHaveTextContent('1 on break');

    // Active deliveries = in transit + out for delivery in the donut.
    expect(await card('Active Deliveries')).toHaveTextContent('12');
    const donut = region("Today's Delivery Status");
    expect(within(donut).getByText('In Transit').closest('div')).toHaveTextContent('12');
    expect(within(donut).getByText('Out for Delivery').closest('div')).toHaveTextContent('0');

    // One failed attempt needs a decision; it is the one listed.
    expect(await card('Failed Today')).toHaveTextContent('1 need a decision');
    const failed = region('Failed deliveries needing a decision');
    expect(await within(failed).findByText('Recipient not available')).toBeInTheDocument();

    // The busiest couriers first: Ramesh has 15 today.
    const workload = region('Courier Workload');
    const rows = await within(workload).findAllByRole('row');
    expect(rows[1]).toHaveTextContent('Ramesh Perera15');
    expect(within(region('Active Deliveries')).getAllByRole('row')).toHaveLength(6); // header + 5
  });

  it('keeps the other blocks when one fails', async () => {
    jest
      .spyOn(dispatchApi, 'activeDeliveries')
      .mockRejectedValue(new ApiRequestError('Service unavailable', { status: 503 }));
    renderPage();

    expect(
      await within(region('Active Deliveries')).findByText(/Service unavailable/),
    ).toBeInTheDocument();
    await waitFor(async () => expect(await card('Unassigned Parcels')).toHaveTextContent('5'));
    expect(
      await within(region('Unassigned Parcels')).findAllByRole('link', { name: /^Assign #SD/ }),
    ).toHaveLength(5);
    expect(within(region('Quick actions')).getAllByRole('link')).toHaveLength(4);
  });

  it('links Assign to the Unassigned page for that parcel', async () => {
    renderPage();
    const assign = await within(region('Unassigned Parcels')).findByRole('link', {
      name: 'Assign #SD123456789',
    });
    expect(assign).toHaveAttribute(
      'href',
      '/admin/dispatch/unassigned?parcelId=a0000000-0000-4000-8000-000000000001',
    );
  });

  it('retries a failed delivery from its block, then refreshes the numbers', async () => {
    renderPage();
    const failed = region('Failed deliveries needing a decision');
    expect(await within(failed).findByRole('link', { name: 'View #SD998877665' })).toHaveAttribute(
      'href',
      `/admin/parcels/${FAILED_ATTEMPT_ID}`,
    );
    await userEvent.click(within(failed).getByRole('button', { name: 'Retry #SD998877665' }));
    const dialog = await screen.findByRole('dialog', { name: 'Retry #SD998877665?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Retry Delivery' }));

    expect(await screen.findByText('Back in Unassigned')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Assign a courier' })).toHaveAttribute(
      'href',
      `/admin/dispatch/unassigned?parcelId=${FAILED_ATTEMPT_ID}`,
    );
    expect(
      await within(failed).findByText('Nothing is waiting for a retry or close.'),
    ).toBeInTheDocument();
    await waitFor(async () => expect(await card('Unassigned Parcels')).toHaveTextContent('6'));
  });

  it('refreshes on the button and shows when it last updated', async () => {
    jest.useFakeTimers({ now: new Date('2026-11-14T05:00:00Z'), advanceTimers: true });
    const overview = jest.spyOn(dispatchApi, 'overview');
    renderPage();
    // 10:30 AM in Sri Lanka.
    expect(screen.getByText('Updated 10:30 AM')).toBeInTheDocument();
    await waitFor(() => expect(overview).toHaveBeenCalledTimes(1));

    jest.setSystemTime(new Date('2026-11-14T05:02:00Z'));
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(screen.getByText('Updated 10:32 AM')).toBeInTheDocument();
    await waitFor(() => expect(overview).toHaveBeenCalledTimes(2));
  });

  it('refreshes every minute, but not while the tab is hidden', async () => {
    jest.useFakeTimers({ now: new Date('2026-11-14T05:00:00Z') });
    const overview = jest.spyOn(dispatchApi, 'overview');
    renderPage();
    expect(overview).toHaveBeenCalledTimes(1);

    await act(async () => {
      jest.advanceTimersByTime(60_000);
    });
    expect(overview).toHaveBeenCalledTimes(2);
    expect(screen.getByText('Updated 10:31 AM')).toBeInTheDocument();

    setHidden(true);
    await act(async () => {
      jest.advanceTimersByTime(120_000);
    });
    expect(overview).toHaveBeenCalledTimes(2);

    // Back after more than a minute: refreshed at once.
    await act(async () => setHidden(false));
    expect(overview).toHaveBeenCalledTimes(3);
  });
});
