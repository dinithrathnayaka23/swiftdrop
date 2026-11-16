import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { resetMockCourierData } from '../mock/mockCourierApi';
import DeliveryDetailsPage from './DeliveryDetailsPage';

const OUT_FOR_DELIVERY = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01';
const WAITING_FOR_PICKUP = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e04';

function renderAt(parcelId: string) {
  render(
    <MemoryRouter initialEntries={[`/courier/deliveries/${parcelId}`]}>
      <Routes>
        <Route path="/courier/deliveries/:parcelId" element={<DeliveryDetailsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DeliveryDetailsPage', () => {
  beforeEach(() => resetMockCourierData());

  it('shows the recipient, parcel and history of a delivery (mock API)', async () => {
    renderAt(OUT_FOR_DELIVERY);

    expect(await screen.findByRole('heading', { name: 'SD2611120001' })).toBeInTheDocument();
    expect(screen.getByText('Kasun Perera')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Call Kasun Perera' })).toHaveAttribute(
      'href',
      'tel:+94771234567',
    );
    expect(screen.getByText('Books')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Status History' })).toBeInTheDocument();
  });

  it('offers only the next steps the backend allows', async () => {
    renderAt(OUT_FOR_DELIVERY);

    expect(await screen.findByRole('link', { name: 'Confirm Delivery' })).toHaveAttribute(
      'href',
      `/courier/deliveries/${OUT_FOR_DELIVERY}/proof`,
    );
    expect(screen.getByRole('link', { name: 'Report Failed Delivery' })).toHaveAttribute(
      'href',
      `/courier/deliveries/${OUT_FOR_DELIVERY}/failed`,
    );
    expect(screen.queryByRole('link', { name: 'Mark as Picked Up' })).not.toBeInTheDocument();
  });

  it('puts the pickup first while the parcel waits to be collected', async () => {
    renderAt(WAITING_FOR_PICKUP);

    expect(await screen.findByRole('link', { name: 'Mark as Picked Up' })).toHaveAttribute(
      'href',
      `/courier/deliveries/${WAITING_FOR_PICKUP}/status?status=PICKED_UP`,
    );
    const labels = screen.getAllByText(/^(Pick up from|Deliver to)$/).map((el) => el.textContent);
    expect(labels).toEqual(['Pick up from', 'Deliver to']);
  });

  it("says when the delivery isn't the courier's", async () => {
    renderAt('00000000-0000-0000-0000-000000000000');

    expect(await screen.findByText('Delivery not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: "Back to today's deliveries" })).toHaveAttribute(
      'href',
      '/courier',
    );
  });
});
