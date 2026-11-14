import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { resetMockCouriers } from '../../../api/mock/mockCouriersApi';
import { resetMockDispatchStates } from '../../../api/mock/mockParcelsApi';
import { resetMockServiceAreas } from '../../../api/mock/mockServiceAreasApi';
import CouriersPage from './CouriersPage';

// The mock API answers after short delays; several steps per test.
jest.setTimeout(20_000);

function renderPage() {
  render(
    <MemoryRouter>
      <CouriersPage />
    </MemoryRouter>,
  );
}

async function rowOf(name: string): Promise<HTMLElement> {
  return (await screen.findByText(name)).closest('tr')!;
}

/** Opens a courier's drawer from the row menu. */
async function openDrawer(name: string): Promise<HTMLElement> {
  const row = await rowOf(name);
  await userEvent.click(within(row).getByRole('button', { name: `More actions for ${name}` }));
  await userEvent.click(screen.getByRole('menuitem', { name: 'View details' }));
  const drawer = await screen.findByRole('dialog', { name: name });
  return drawer;
}

describe('CouriersPage (mock API)', () => {
  beforeEach(() => {
    resetMockServiceAreas();
    resetMockCouriers();
    resetMockDispatchStates();
  });

  it('shows the summary cards and the workload of each courier', async () => {
    renderPage();
    const summary = screen.getByRole('region', { name: 'Courier summary' });
    expect(await within(summary).findByText('11')).toBeInTheDocument(); // active couriers only

    const ramesh = await rowOf('Ramesh Perera');
    expect(within(ramesh).getByText('12 / 15')).toBeInTheDocument();
    expect(within(ramesh).getByText('Online')).toBeInTheDocument();
    expect(within(ramesh).getByText('Colombo 01, Colombo 02-07')).toBeInTheDocument();
  });

  it('assigns parcels to an online courier from the row, and shows View for the others', async () => {
    renderPage();
    const onBreak = await rowOf('Kasun Perera');
    expect(within(onBreak).getByRole('button', { name: 'View Kasun Perera' })).toBeInTheDocument();
    expect(within(onBreak).queryByRole('button', { name: /^Assign/ })).not.toBeInTheDocument();

    const ramesh = await rowOf('Ramesh Perera');
    await userEvent.click(
      within(ramesh).getByRole('button', { name: 'Assign parcels to Ramesh Perera' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Assign Parcels to Ramesh Perera' });
    expect(within(dialog).getByRole('button', { name: 'Assign 0 Parcels' })).toBeDisabled();
    await userEvent.click(
      await within(dialog).findByRole('checkbox', { name: 'Select #SD123456789' }),
    );
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Select #SD667788990' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Assign 2 Parcels' }));

    expect(await within(dialog).findByText('2 assigned')).toBeInTheDocument();
    // The list behind refreshes: Ramesh has two more deliveries today.
    await waitFor(async () =>
      expect(within(await rowOf('Ramesh Perera')).getByText('12 / 17')).toBeInTheDocument(),
    );
    // Both parcels left the waiting list.
    await waitFor(() =>
      expect(
        within(dialog).queryByRole('checkbox', { name: 'Select #SD123456789' }),
      ).not.toBeInTheDocument(),
    );
  });

  it('opens the same dialog from the drawer’s Assign Deliveries', async () => {
    renderPage();
    const drawer = await openDrawer('Ramesh Perera');
    await userEvent.click(await within(drawer).findByRole('button', { name: 'Assign Deliveries' }));
    expect(
      await screen.findByRole('dialog', { name: 'Assign Parcels to Ramesh Perera' }),
    ).toBeInTheDocument();
  });

  it('reassigns a current delivery: a reason and a courier are required', async () => {
    renderPage();
    const drawer = await openDrawer('Ramesh Perera');
    await userEvent.click(within(drawer).getByRole('tab', { name: 'Current Deliveries' }));
    const [first] = await within(drawer).findAllByRole('button', { name: /^Reassign #SD/ });
    await userEvent.click(first);
    const dialog = await screen.findByRole('dialog', { name: /^Reassign #SD/ });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }));
    expect(within(dialog).getByText('Reason is required')).toBeInTheDocument();
    expect(within(dialog).getByText('Choose a courier')).toBeInTheDocument();
    // The parcel's own courier is not offered.
    await within(dialog).findByRole('radio', { name: /Dilshan Silva/ });
    expect(within(dialog).queryByRole('radio', { name: /Ramesh Perera/ })).not.toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Vehicle broke down');
    await userEvent.click(within(dialog).getByRole('radio', { name: /Dilshan Silva/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }));

    expect(
      await within(drawer).findByText(/was reassigned to Dilshan Silva\.$/),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(within(drawer).getAllByRole('link', { name: /^#SD/ })).toHaveLength(2),
    );
  });

  it('unassigns a delivery that is still waiting for pickup', async () => {
    renderPage();
    const drawer = await openDrawer('Ramesh Perera');
    await userEvent.click(within(drawer).getByRole('tab', { name: 'Current Deliveries' }));
    // One waiting parcel (Unassign) and two picked up (Reassign only).
    expect(await within(drawer).findAllByRole('button', { name: /^Reassign #SD/ })).toHaveLength(3);
    const unassign = within(drawer).getAllByRole('button', { name: /^Unassign #SD/ });
    expect(unassign).toHaveLength(1);

    await userEvent.click(unassign[0]);
    const dialog = await screen.findByRole('dialog', { name: /^Unassign #SD/ });
    await userEvent.type(within(dialog).getByLabelText(/Reason/), 'Customer asked to wait');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Unassign' }));

    expect(
      await within(drawer).findByText(/was unassigned and is waiting for a courier again\.$/),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(within(drawer).getAllByRole('link', { name: /^#SD/ })).toHaveLength(2),
    );
  });

  it('filters by status and searches after a short pause', async () => {
    renderPage();
    await rowOf('Ramesh Perera');

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'ON_BREAK');
    expect(await rowOf('Kasun Perera')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Ramesh Perera')).not.toBeInTheDocument());

    await userEvent.selectOptions(screen.getByLabelText('Status'), '');
    await userEvent.type(screen.getByLabelText('Search'), 'dilshan');
    // The search waits for a pause in typing, then the list loads again with only the match.
    await waitFor(() => expect(screen.queryByText('Ramesh Perera')).not.toBeInTheDocument(), {
      timeout: 3000,
    });
    expect(await screen.findByText('Dilshan Silva')).toBeInTheDocument();
  });

  it('opens the drawer with its tabs and links each delivery to the parcel', async () => {
    renderPage();
    const drawer = await openDrawer('Ramesh Perera');
    expect(await within(drawer).findByText('ramesh.perera@swiftdrop.lk')).toBeInTheDocument();
    expect(within(drawer).getByText('ABC-1234')).toBeInTheDocument();

    await userEvent.click(within(drawer).getByRole('tab', { name: 'Current Deliveries' }));
    const current = await within(drawer).findAllByRole('link', { name: /^#SD/ });
    expect(current).toHaveLength(3);
    expect(current[0]).toHaveAttribute('href', expect.stringMatching(/^\/admin\/parcels\//));

    await userEvent.click(within(drawer).getByRole('tab', { name: 'History' }));
    expect((await within(drawer).findAllByText('Completed')).length).toBeGreaterThan(0);
    expect(within(drawer).getByRole('navigation', { name: 'Pagination' })).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('changes availability from the drawer and refuses Online for a deactivated courier', async () => {
    renderPage();
    const drawer = await openDrawer('Kasun Perera');

    await userEvent.click(await within(drawer).findByRole('radio', { name: 'Online' }));
    await waitFor(() =>
      expect(within(drawer).getByRole('radio', { name: 'Online' })).toHaveAttribute(
        'aria-checked',
        'true',
      ),
    );
    await userEvent.click(within(drawer).getByRole('button', { name: 'Close courier details' }));

    const chamari = await openDrawer('Chamari Dias');
    await userEvent.click(await within(chamari).findByRole('radio', { name: 'Online' }));
    expect(
      await within(chamari).findByText('This courier is deactivated and cannot go online'),
    ).toBeInTheDocument();
  });

  it('marks a service area that is no longer active and drops it when saving', async () => {
    renderPage();
    const drawer = await openDrawer('Praveen Silva');
    const anuradhapura = (await within(drawer).findByText('Anuradhapura')).closest('li')!;
    // Shown once the active zones have loaded.
    expect(await within(anuradhapura).findByText('inactive')).toBeInTheDocument();

    await userEvent.click(within(drawer).getByRole('button', { name: 'Edit service areas' }));
    const modal = await screen.findByRole('dialog', { name: 'Edit Vehicle and Service Areas' });
    expect(
      await within(modal).findByText('Inactive areas are removed when you save.'),
    ).toBeInTheDocument();
    await userEvent.click(within(modal).getByRole('button', { name: 'Save Changes' }));

    expect(await screen.findByText('Praveen Silva was updated.')).toBeInTheDocument();
    // The drawer loads the courier again: only the active area is left.
    expect(await within(drawer).findByText('Kurunegala')).toBeInTheDocument();
    expect(within(drawer).queryByText('Anuradhapura')).not.toBeInTheDocument();
  });

  it('checks the Add Courier form like the backend and shows a 409 on the field', async () => {
    renderPage();
    await rowOf('Ramesh Perera');
    await userEvent.click(screen.getByRole('button', { name: 'Add Courier' }));
    const modal = await screen.findByRole('dialog', { name: 'Add Courier' });

    await userEvent.click(within(modal).getByRole('button', { name: 'Add Courier' }));
    expect(within(modal).getByText('Full name is required')).toBeInTheDocument();
    expect(within(modal).getByText('Phone number is required')).toBeInTheDocument();
    expect(within(modal).getByText('Vehicle registration is required')).toBeInTheDocument();

    await userEvent.type(within(modal).getByLabelText('Full Name'), 'R2D2');
    await userEvent.type(within(modal).getByLabelText('Email'), 'ramesh.perera@swiftdrop.lk');
    await userEvent.type(within(modal).getByLabelText('Phone Number'), '77 123 9876');
    await userEvent.type(within(modal).getByLabelText(/First Password/), 'password');
    await userEvent.selectOptions(within(modal).getByLabelText('Vehicle Type'), 'Motorbike');
    await userEvent.type(within(modal).getByLabelText('Vehicle Registration'), 'WP@1');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Courier' }));
    expect(within(modal).getByText(/Full name can only contain letters/)).toBeInTheDocument();
    expect(within(modal).getByText(/at least one letter and one number/)).toBeInTheDocument();
    expect(within(modal).getByText(/letters, numbers, spaces and hyphens/)).toBeInTheDocument();

    const name = within(modal).getByLabelText('Full Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Amaya Gunawardena');
    const password = within(modal).getByLabelText(/First Password/);
    await userEvent.clear(password);
    await userEvent.type(password, 'courier2026');
    const registration = within(modal).getByLabelText('Vehicle Registration');
    await userEvent.clear(registration);
    await userEvent.type(registration, 'WP CAB-9001');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Courier' }));

    // The email belongs to Ramesh Perera: the backend's 409 is shown on the email field.
    expect(
      await within(modal).findByText('An account with this email already exists'),
    ).toBeInTheDocument();
    expect(within(modal).getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');

    const email = within(modal).getByLabelText('Email');
    await userEvent.clear(email);
    await userEvent.type(email, 'amaya@example.com');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Courier' }));
    expect(
      await screen.findByText('Amaya Gunawardena was added. They start Offline.'),
    ).toBeInTheDocument();
  });
});
