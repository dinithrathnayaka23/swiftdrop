import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { mockCouriersApi, resetMockCouriers } from '../../../api/mock/mockCouriersApi';
import { mockDispatchApi } from '../../../api/mock/mockDispatchApi';
import { resetMockDispatchStates } from '../../../api/mock/mockParcelsApi';
import { resetMockServiceAreas } from '../../../api/mock/mockServiceAreasApi';
import { parcelsApi } from '../../../api/parcels';
import type { AdminParcelRow } from '../../../types/parcel';
import UnassignedParcelsPage from './UnassignedParcelsPage';

// The mock API answers after short delays; several steps per test.
jest.setTimeout(20_000);

const SD123456789 = 'a0000000-0000-4000-8000-000000000001';
const SD456789123 = 'a0000000-0000-4000-8000-000000000003'; // in transit
const SD112233445 = 'a0000000-0000-4000-8000-000000000006';

function renderPage(url = '/admin/dispatch/unassigned') {
  render(
    <MemoryRouter initialEntries={[url]}>
      <UnassignedParcelsPage />
    </MemoryRouter>,
  );
}

async function rowOf(tracking: string): Promise<HTMLElement> {
  return (await screen.findByRole('link', { name: `#${tracking}` })).closest('tr')!;
}

async function courierId(name: string): Promise<string> {
  return (await mockCouriersApi.list({ search: name })).content[0].id;
}

/** Picks a courier in the open dialog once the online couriers have loaded. */
async function pickCourier(dialog: HTMLElement, name: string) {
  await userEvent.click(await within(dialog).findByRole('radio', { name: new RegExp(name) }));
}

describe('UnassignedParcelsPage (mock API)', () => {
  beforeEach(() => {
    resetMockServiceAreas();
    resetMockCouriers();
    resetMockDispatchStates();
  });
  afterEach(() => jest.restoreAllMocks());

  it('lists only waiting parcels and searches after a short pause', async () => {
    renderPage();
    const row = await rowOf('SD123456789');
    expect(within(row).getByText('BrightMart')).toBeInTheDocument();
    expect(within(row).getByText('Colombo 07')).toBeInTheDocument();
    expect(within(row).getByText('2.5 kg')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '#SD456789123' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '#SD987654321' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Assign selected \(0\)/ })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Search'), 'samanthi');
    await waitFor(
      () => expect(screen.queryByRole('link', { name: '#SD123456789' })).not.toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(await rowOf('SD667788990')).toBeInTheDocument();
  });

  it('assigns one parcel to an online courier', async () => {
    renderPage();
    await userEvent.click(
      within(await rowOf('SD123456789')).getByRole('button', { name: 'Assign #SD123456789' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Assign #SD123456789' });
    // Only couriers who can take parcels now are offered.
    expect(within(dialog).queryByRole('radio', { name: /Kasun Perera/ })).not.toBeInTheDocument();
    await pickCourier(dialog, 'Ramesh Perera');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Assign' }));

    expect(
      await screen.findByText('#SD123456789 was assigned to Ramesh Perera.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: '#SD123456789' })).not.toBeInTheDocument(),
    );
  });

  it('shows the backend’s 409 in the dialog when the courier is no longer online', async () => {
    renderPage();
    await userEvent.click(
      within(await rowOf('SD123456789')).getByRole('button', { name: 'Assign #SD123456789' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Assign #SD123456789' });
    await pickCourier(dialog, 'Ramesh Perera');
    // Ramesh goes offline while the dialog is open.
    await mockCouriersApi.changeAvailability(await courierId('Ramesh'), 'OFFLINE');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Assign' }));

    expect(
      await within(dialog).findByText('This courier is offline and cannot be given parcels'),
    ).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Assign #SD123456789' })).toBeInTheDocument();
  });

  it('bulk assigns, reports the failed parcel and keeps only it selected', async () => {
    renderPage();
    for (const tracking of ['SD123456789', 'SD112233445', 'SD667788990']) {
      await userEvent.click(
        within(await rowOf(tracking)).getByRole('checkbox', { name: `Select #${tracking}` }),
      );
    }
    // Another dispatcher assigns one of them first.
    await mockDispatchApi.assign(SD112233445, await courierId('Dilshan'));

    await userEvent.click(screen.getByRole('button', { name: /Assign selected \(3\)/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Assign 3 Parcels' });
    await pickCourier(dialog, 'Ramesh Perera');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Assign 3 Parcels' }));

    expect(await within(dialog).findByText('2 assigned, 1 failed')).toBeInTheDocument();
    const failed = within(dialog).getByRole('list', { name: 'Parcels not assigned' });
    expect(failed).toHaveTextContent('#SD112233445: This parcel is already assigned to a courier');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Done' }));

    expect(await screen.findByRole('button', { name: /Assign selected \(1\)/ })).toBeEnabled();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: '#SD123456789' })).not.toBeInTheDocument(),
    );
  });

  it('allows at most 50 parcels per bulk assignment', async () => {
    const many: AdminParcelRow[] = Array.from({ length: 51 }, (_, i) => ({
      id: `p${i}`,
      trackingNumber: `SD9${String(i).padStart(8, '0')}`,
      merchantName: null,
      recipientName: 'Recipient',
      destination: 'Kandy',
      weightKg: 1,
      status: 'CREATED',
      dispatchState: 'UNASSIGNED',
      createdAt: new Date().toISOString(),
    }));
    jest.spyOn(parcelsApi, 'listAdminParcels').mockResolvedValue({
      content: many,
      page: { size: 100, number: 0, totalElements: 51, totalPages: 1 },
    });
    renderPage();
    await userEvent.click(
      await screen.findByRole('checkbox', { name: 'Select all parcels on this page' }),
    );

    expect(screen.getByRole('button', { name: /Assign selected \(51\)/ })).toBeDisabled();
    expect(
      screen.getByText(/At most 50 parcels can be assigned at once\. Deselect 1 to continue\./),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select #SD900000000' }));
    expect(screen.getByRole('button', { name: /Assign selected \(50\)/ })).toBeEnabled();
  });

  it('opens the dialog for ?parcelId= when the parcel is waiting', async () => {
    renderPage(`/admin/dispatch/unassigned?parcelId=${SD123456789}`);
    expect(await screen.findByRole('dialog', { name: 'Assign #SD123456789' })).toBeInTheDocument();
  });

  it('ignores ?parcelId= for a parcel that is not waiting', async () => {
    renderPage(`/admin/dispatch/unassigned?parcelId=${SD456789123}`);
    expect(
      await screen.findByText(
        'That parcel is not waiting for a courier, so there is nothing to assign.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
