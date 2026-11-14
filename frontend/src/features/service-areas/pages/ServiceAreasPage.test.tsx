import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { resetMockServiceAreas } from '../../../api/mock/mockServiceAreasApi';
import ServiceAreasPage from './ServiceAreasPage';

// The mock API answers after short delays; several steps per test.
jest.setTimeout(20_000);

function renderPage() {
  render(
    <MemoryRouter>
      <ServiceAreasPage />
    </MemoryRouter>,
  );
}

async function rowOf(name: string): Promise<HTMLElement> {
  return (await screen.findByText(name, { selector: 'p' })).closest('tr')!;
}

/** Fills the last coverage row of the form. */
async function fillLastRow(modal: HTMLElement, district: string, city: string, postalCode = '') {
  const last = (label: RegExp) => within(modal).getAllByLabelText(label).at(-1)!;
  await userEvent.type(last(/^District, new row/), district);
  await userEvent.type(last(/^City, new row/), city);
  if (postalCode) await userEvent.type(last(/^Postal code \(optional\), new row/), postalCode);
}

describe('ServiceAreasPage (mock API)', () => {
  beforeEach(() => resetMockServiceAreas());

  it('lists zones with their places, postal range, coverage and status', async () => {
    renderPage();
    const kandy = await rowOf('Kandy');
    expect(within(kandy).getByText('Kandy, Katugastota, Peradeniya')).toBeInTheDocument();
    expect(within(kandy).getByText('20000 – 20800')).toBeInTheDocument();
    expect(within(kandy).getByText('3 places')).toBeInTheDocument();
    expect(within(kandy).getByText('Active')).toBeInTheDocument();
  });

  it('searches after a short pause and filters by status', async () => {
    renderPage();
    await rowOf('Kandy');

    await userEvent.type(screen.getByLabelText('Search'), 'unawat');
    await waitFor(
      () => expect(screen.queryByText('Kandy', { selector: 'p' })).not.toBeInTheDocument(),
      {
        timeout: 3000,
      },
    );
    expect(await rowOf('Galle')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Search'));
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'inactive');
    const anuradhapura = await rowOf('Anuradhapura');
    expect(within(anuradhapura).getByText('Inactive')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByText('Galle', { selector: 'p' })).not.toBeInTheDocument(),
    );
  });

  it('adds a service area with its coverage rows', async () => {
    renderPage();
    await rowOf('Kandy');
    await userEvent.click(screen.getByRole('button', { name: 'Add Service Area' }));
    const modal = await screen.findByRole('dialog', { name: 'Add Service Area' });

    await userEvent.type(within(modal).getByLabelText('Zone Name'), 'Hill Country');
    await fillLastRow(modal, 'Nuwara Eliya', 'Nuwara Eliya', '22200');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Row' }));
    await fillLastRow(modal, 'Nuwara Eliya', 'Hatton');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Service Area' }));

    expect(await screen.findByText('Hill Country was saved.')).toBeInTheDocument();
    const row = await rowOf('Hill Country');
    expect(within(row).getByText('Hatton, Nuwara Eliya')).toBeInTheDocument();
  });

  it('checks the form and shows the backend’s errors: a taken name and an overlap naming the zone', async () => {
    renderPage();
    await rowOf('Kandy');
    await userEvent.click(screen.getByRole('button', { name: 'Add Service Area' }));
    const modal = await screen.findByRole('dialog', { name: 'Add Service Area' });

    await userEvent.click(within(modal).getByRole('button', { name: 'Add Service Area' }));
    expect(within(modal).getByText('Name is required')).toBeInTheDocument();
    expect(within(modal).getByText('District and city are required')).toBeInTheDocument();

    await userEvent.type(within(modal).getByLabelText('Zone Name'), 'KANDY');
    await fillLastRow(modal, 'Kandy', 'Gampola');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Service Area' }));
    expect(
      await within(modal).findByText('A service area with this name already exists'),
    ).toBeInTheDocument();

    const name = within(modal).getByLabelText('Zone Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Hill Country');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Row' }));
    await fillLastRow(modal, 'Kandy', 'Peradeniya', '20400');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Service Area' }));
    expect(
      await within(modal).findByText(
        'Peradeniya, Kandy (20400) is already covered by the active service area "Kandy"',
      ),
    ).toBeInTheDocument();
  });

  it('deactivates and activates a zone after confirming, and shows the overlap 409 on activate', async () => {
    renderPage();
    const galle = await rowOf('Galle');
    await userEvent.click(within(galle).getByRole('button', { name: 'More actions for Galle' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Deactivate' }));
    const confirm = screen.getByRole('alertdialog', { name: 'Deactivate Galle?' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByText('Galle is now inactive.')).toBeInTheDocument();
    await waitFor(async () =>
      expect(within(await rowOf('Galle')).getByText('Inactive')).toBeInTheDocument(),
    );

    // Anuradhapura (inactive) gets a place Kandy covers; activating it is then refused.
    await userEvent.click(
      within(await rowOf('Anuradhapura')).getByRole('button', { name: 'Edit Anuradhapura' }),
    );
    const modal = await screen.findByRole('dialog', { name: 'Edit Service Area' });
    await userEvent.click(await within(modal).findByRole('button', { name: 'Add Row' }));
    await fillLastRow(modal, 'Kandy', 'Kandy', '20000');
    await userEvent.click(within(modal).getByRole('button', { name: 'Save Changes' }));
    expect(await screen.findByText('Anuradhapura was saved.')).toBeInTheDocument();

    const anuradhapura = await rowOf('Anuradhapura');
    await userEvent.click(
      within(anuradhapura).getByRole('button', { name: 'More actions for Anuradhapura' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Activate' }));
    await userEvent.click(
      within(screen.getByRole('alertdialog', { name: 'Activate Anuradhapura?' })).getByRole(
        'button',
        {
          name: 'Activate',
        },
      ),
    );
    expect(
      await screen.findByText(
        'Kandy, Kandy (20000) is already covered by the active service area "Kandy"',
      ),
    ).toBeInTheDocument();
  });

  it('reloads the zone and marks the row when one coverage request fails', async () => {
    renderPage();
    await userEvent.click(
      within(await rowOf('Kurunegala')).getByRole('button', { name: 'Edit Kurunegala' }),
    );
    const modal = await screen.findByRole('dialog', { name: 'Edit Service Area' });
    await within(modal).findByText('Kurunegala, Kurunegala (60000)');

    await userEvent.click(within(modal).getByRole('button', { name: 'Add Row' }));
    await fillLastRow(modal, 'Kurunegala', 'Kuliyapitiya', '60200');
    await userEvent.click(within(modal).getByRole('button', { name: 'Add Row' }));
    await fillLastRow(modal, 'Galle', 'Galle', '80000'); // Galle (active) already covers it
    await userEvent.click(within(modal).getByRole('button', { name: 'Save Changes' }));

    expect(
      await within(modal).findByText(
        /Couldn't add Galle, Galle \(80000\): .*"Galle"\. The changes before it were saved/,
      ),
    ).toBeInTheDocument();
    // Reloaded: Kuliyapitiya is saved and listed; the failed row stays, marked, for another try.
    expect(within(modal).getByText('Kuliyapitiya, Kurunegala (60200)')).toBeInTheDocument();
    const remaining = within(modal).getAllByLabelText(/^City, new row/);
    expect(remaining).toHaveLength(1);
    expect(remaining[0]).toHaveValue('Galle');
    expect(remaining[0]).toHaveAttribute('aria-invalid', 'true');
    expect(remaining[0].closest('li')).toHaveTextContent(
      'is already covered by the active service area "Galle"',
    );
  });
});
