import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import ParcelListPage from './ParcelListPage';

function renderPage() {
  render(
    <MemoryRouter>
      <ParcelListPage />
    </MemoryRouter>,
  );
}

describe('ParcelListPage', () => {
  it('lists parcels with an Assign button for unassigned ones (mock API)', async () => {
    renderPage();
    const row = (await screen.findByText('#SD123456789')).closest('tr')!;
    expect(within(row).getByText('Unassigned')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: /Assign/ })).toBeInTheDocument();
  });

  it('shows only unassigned parcels on the Unassigned tab', async () => {
    renderPage();
    await screen.findByText('#SD123456789');

    await userEvent.click(screen.getByRole('tab', { name: /Unassigned/ }));

    expect(await screen.findByText('#SD667788990')).toBeInTheDocument();
    expect(screen.queryByText('#SD456789123')).not.toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).queryByText('Delivered')).not.toBeInTheDocument();
  });

  it('opens the details of a parcel', async () => {
    renderPage();
    const row = (await screen.findByText('#SD456789123')).closest('tr')!;

    await userEvent.click(within(row).getByRole('button', { name: /View/ }));

    expect(
      (await screen.findAllByRole('heading', { name: '#SD456789123' })).length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText('Dinesh Silva').length).toBeGreaterThan(1);
    expect(screen.getAllByRole('tab', { name: 'History' }).length).toBeGreaterThan(0);
  });
});
