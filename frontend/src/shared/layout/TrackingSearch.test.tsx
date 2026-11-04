import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router';
import TrackingSearch from './TrackingSearch';

function TrackResult() {
  return <p>tracking {useParams().trackingNumber}</p>;
}

function renderSearch() {
  render(
    <MemoryRouter initialEntries={['/customer']}>
      <TrackingSearch />
      <Routes>
        <Route path="/customer" element={<p>dashboard</p>} />
        <Route path="/track/:trackingNumber" element={<TrackResult />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('TrackingSearch', () => {
  it('opens the tracking page for the number entered', async () => {
    renderSearch();

    await userEvent.type(screen.getByRole('searchbox'), ' sd123456789{Enter}');

    expect(screen.getByText('tracking SD123456789')).toBeInTheDocument();
    expect(screen.getByRole('searchbox')).toHaveValue('');
  });

  it('explains an invalid number and stays on the page', async () => {
    renderSearch();

    await userEvent.type(screen.getByRole('searchbox'), 'ab{Enter}');

    expect(screen.getByRole('alert')).toHaveTextContent('Enter a valid tracking number');
    expect(screen.getByText('dashboard')).toBeInTheDocument();
  });

  it('is focused with Ctrl+K', async () => {
    renderSearch();

    await userEvent.keyboard('{Control>}k{/Control}');

    expect(screen.getByRole('searchbox')).toHaveFocus();
  });
});
