import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { dispatchApi } from '../../../api/dispatch';
import { resetMockCouriers } from '../../../api/mock/mockCouriersApi';
import {
  mockDispatchApi,
  resetMockFailedDeliveries,
  setMockDefaultFailedAction,
} from '../../../api/mock/mockDispatchApi';
import { mockParcelsApi, resetMockDispatchStates } from '../../../api/mock/mockParcelsApi';
import { resetMockServiceAreas } from '../../../api/mock/mockServiceAreasApi';
import type { FailedDelivery } from '../../../types/dispatch';
import FailedDeliveriesPage from './FailedDeliveriesPage';

// The mock API answers after short delays; several steps per test.
jest.setTimeout(20_000);

/** SD998877665: the sample parcel in FAILED_ATTEMPT. */
const FAILED_ATTEMPT_ID = 'a0000000-0000-4000-8000-000000000007';

function renderPage() {
  render(
    <MemoryRouter>
      <FailedDeliveriesPage />
    </MemoryRouter>,
  );
}

async function rowOf(tracking: string): Promise<HTMLElement> {
  return (await screen.findByRole('link', { name: `#${tracking}` })).closest('tr')!;
}

describe('FailedDeliveriesPage (mock API)', () => {
  beforeEach(() => {
    resetMockServiceAreas();
    resetMockCouriers();
    resetMockDispatchStates();
    resetMockFailedDeliveries();
  });
  afterEach(() => jest.restoreAllMocks());

  it('shows the parcels needing a decision and the closed ones on their tabs', async () => {
    renderPage();
    const row = await rowOf('SD998877665');
    expect(within(row).getByText('Recipient not available')).toBeInTheDocument();
    expect(within(row).getByText('Kottawa')).toBeInTheDocument();
    expect(within(row).getByText('2')).toBeInTheDocument(); // attempts
    expect(within(row).getByRole('link', { name: '#SD998877665' })).toHaveAttribute(
      'href',
      `/admin/parcels/${FAILED_ATTEMPT_ID}`,
    );
    expect(within(row).getByRole('button', { name: 'Retry #SD998877665' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Closed' }));
    const closed = await rowOf('SD510055433');
    expect(within(closed).getByText('Delivery closed after a failed attempt')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '#SD510134623' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Retry/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Close/ })).not.toBeInTheDocument();
  });

  it('retries with a note and links the parcel back in Unassigned', async () => {
    renderPage();
    await userEvent.click(
      within(await rowOf('SD998877665')).getByRole('button', { name: 'Retry #SD998877665' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Retry #SD998877665?' });
    await userEvent.type(within(dialog).getByLabelText(/Note/), 'Recipient is home after 5 PM');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Retry Delivery' }));

    expect(await screen.findByText('Back in Unassigned')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Assign a courier' })).toHaveAttribute(
      'href',
      `/admin/dispatch/unassigned?parcelId=${FAILED_ATTEMPT_ID}`,
    );
    expect(await screen.findByText('Nothing Needs a Decision')).toBeInTheDocument();
    const unassigned = await mockParcelsApi.listAdminParcels({ tab: 'UNASSIGNED', size: 50 });
    expect(unassigned.content.map((p) => p.id)).toContain(FAILED_ATTEMPT_ID);
  });

  it('closes with a required reason and lists the parcel as closed', async () => {
    renderPage();
    await userEvent.click(
      within(await rowOf('SD998877665')).getByRole('button', { name: 'Close #SD998877665' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Close #SD998877665?' });
    // RETRY_DELIVERY is the default: nothing is pre-filled.
    expect(within(dialog).getByLabelText(/Reason/)).toHaveValue('');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Close Delivery' }));
    expect(within(dialog).getByText('Reason is required')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Address does not exist');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Close Delivery' }));
    expect(await screen.findByText('#SD998877665 was closed as failed.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: 'Closed' }));
    const closed = await rowOf('SD998877665');
    expect(within(closed).getByText('Address does not exist')).toBeInTheDocument();
  });

  it.each([
    ['RETURN_TO_SENDER', 'Returned to sender'],
    ['HOLD_FOR_COLLECTION', 'Held for collection'],
  ] as const)('pre-fills the close reason for %s', async (action, reason) => {
    setMockDefaultFailedAction(action);
    renderPage();
    const row = await rowOf('SD998877665');
    // Wait for the default action before opening the dialog.
    await waitFor(() =>
      expect(within(row).getByRole('button', { name: 'Close #SD998877665' })).toHaveClass(
        'bg-red-600',
      ),
    );
    await userEvent.click(within(row).getByRole('button', { name: 'Close #SD998877665' }));
    const dialog = await screen.findByRole('dialog', { name: 'Close #SD998877665?' });
    expect(within(dialog).getByLabelText(/Reason/)).toHaveValue(reason);
  });

  it('shows the backend’s 409 in the dialog when the parcel was already decided', async () => {
    renderPage();
    await userEvent.click(
      within(await rowOf('SD998877665')).getByRole('button', { name: 'Retry #SD998877665' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Retry #SD998877665?' });
    // Another dispatcher closes it first.
    await mockDispatchApi.close(FAILED_ATTEMPT_ID, 'Closed elsewhere');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Retry Delivery' }));

    expect(
      await within(dialog).findByText(
        'Only a parcel whose delivery attempt failed can be retried or closed',
      ),
    ).toBeInTheDocument();
  });

  it('pages through the queue with the page size from settings', async () => {
    const all: FailedDelivery[] = Array.from({ length: 25 }, (_, i) => ({
      parcelId: `p${i}`,
      trackingNumber: `SD7${String(i).padStart(8, '0')}`,
      parcelStatus: 'FAILED_ATTEMPT',
      failureReason: 'Recipient not available',
      failedAt: new Date().toISOString(),
      courierId: null,
      courierName: null,
      attemptCount: 1,
      destinationCity: 'Kandy',
    }));
    const list = jest
      .spyOn(dispatchApi, 'listFailed')
      .mockImplementation(async ({ page = 0, size = 20 }) => ({
        content: all.slice(page * size, page * size + size),
        page: { size, number: page, totalElements: all.length, totalPages: Math.ceil(25 / size) },
      }));
    renderPage();
    expect(await screen.findByRole('link', { name: '#SD700000000' })).toBeInTheDocument();
    expect(screen.getByText(/Showing 1–10 of 25 parcels/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(await screen.findByRole('link', { name: '#SD700000010' })).toBeInTheDocument();
    expect(list).toHaveBeenLastCalledWith({ scope: 'ATTENTION', page: 1, size: 10 });
  });
});
