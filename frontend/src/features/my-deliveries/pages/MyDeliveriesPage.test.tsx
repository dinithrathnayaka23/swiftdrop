import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import MyDeliveriesPage from './MyDeliveriesPage';

function renderPage(path = '/customer/deliveries') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <MyDeliveriesPage />
    </MemoryRouter>,
  );
}

describe('MyDeliveriesPage', () => {
  it('lists the deliveries with their fee and status (mock API)', async () => {
    renderPage();
    const row = (await screen.findAllByText('SD2508240012'))[0].closest('tr')!;
    expect(within(row).getByText('Rs. 750.00')).toBeInTheDocument();
    expect(within(row).getByText('In Transit')).toBeInTheDocument();
    expect(screen.getByText('Showing 1 to 8 of 8 deliveries')).toBeInTheDocument();
  });

  it('filters by status', async () => {
    renderPage();
    await screen.findAllByText('SD2508240012');

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'FAILED');

    expect(await screen.findByText('Showing 1 to 1 of 1 deliveries')).toBeInTheDocument();
    expect(screen.getAllByText('SD2508150021').length).toBeGreaterThan(0);
  });

  it('opens with the status from the link (e.g. from a dashboard card)', async () => {
    renderPage('/customer/deliveries?status=DELIVERED');
    expect(await screen.findByText('Showing 1 to 5 of 5 deliveries')).toBeInTheDocument();
  });

  it('says when nothing matches the search', async () => {
    renderPage();
    await screen.findAllByText('SD2508240012');

    await userEvent.type(screen.getByLabelText('Search deliveries'), 'nowhere');

    expect(await screen.findByText('No deliveries match your filters')).toBeInTheDocument();
  });
});
