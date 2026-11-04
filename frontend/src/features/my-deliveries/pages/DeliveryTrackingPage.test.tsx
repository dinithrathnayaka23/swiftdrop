import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import DeliveryTrackingPage from './DeliveryTrackingPage';

function renderAt(trackingNumber: string) {
  render(
    <MemoryRouter initialEntries={[`/customer/deliveries/${trackingNumber}`]}>
      <Routes>
        <Route path="/customer/deliveries/:trackingNumber" element={<DeliveryTrackingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DeliveryTrackingPage', () => {
  it('shows the parcel, its addresses, fee and history (mock API)', async () => {
    renderAt('SD2508240012');

    expect(await screen.findByText('SD2508240012')).toBeInTheDocument();
    expect(screen.getByText(/Hassan Ali/)).toBeInTheDocument();
    expect(screen.getAllByText('Rs. 750.00')).toHaveLength(2);
    expect(screen.getByRole('heading', { name: 'Status History' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Download Receipt/ })).toBeInTheDocument();
  });

  it('shows the failure reason of a failed delivery', async () => {
    renderAt('SD2508150021');

    expect(await screen.findByText('SD2508150021')).toBeInTheDocument();
    expect(screen.getAllByText('Reason: Recipient not available').length).toBeGreaterThan(0);
  });

  it('shows "Delivery Not Found" for a parcel that is not the customer\'s', async () => {
    renderAt('SD0000000000');
    expect(await screen.findByText('Delivery Not Found')).toBeInTheDocument();
  });
});
