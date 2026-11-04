import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import TrackingPage from './TrackingPage';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/track" element={<TrackingPage />} />
        <Route path="/track/:trackingNumber" element={<TrackingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('TrackingPage', () => {
  it('shows the parcel and its status (mock API)', async () => {
    renderAt('/track/SD123456789');
    expect(await screen.findByRole('heading', { name: '#SD123456789' })).toBeInTheDocument();
    expect(screen.getAllByText('In Transit').length).toBeGreaterThan(0);
  });

  it('shows "Parcel Not Found" for an unknown tracking number', async () => {
    renderAt('/track/SD000000000');
    expect(await screen.findByText('Parcel Not Found')).toBeInTheDocument();
  });
});
