import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import TodaysDeliveriesPage from './TodaysDeliveriesPage';

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/courier']}>
      <TodaysDeliveriesPage />
    </MemoryRouter>,
  );
}

describe('TodaysDeliveriesPage', () => {
  beforeEach(() => {
    resetMockCourierData();
    jest.restoreAllMocks();
  });

  it('lists the deliveries oldest first with the counts (mock API)', async () => {
    renderPage();

    const cards = await screen.findAllByRole('link', { name: /SD26111200/ });
    expect(cards.map((card) => within(card).getByText(/^SD/).textContent)).toEqual([
      'SD2611120001',
      'SD2611120002',
      'SD2611120003',
      'SD2611120004',
      'SD2611120005',
    ]);
    const counts = screen.getByLabelText('Delivery counts');
    expect(within(counts).getByText('To pick up').previousSibling).toHaveTextContent('2');
    expect(within(counts).getByText('In progress').previousSibling).toHaveTextContent('3');
    expect(within(counts).getByText('Total').previousSibling).toHaveTextContent('5');
  });

  it('shows where to pick up a parcel that is still waiting, and where to deliver the others', async () => {
    renderPage();

    const waiting = (await screen.findByText('SD2611120004')).closest('a')!;
    expect(within(waiting).getByText('Pick up from')).toBeInTheDocument();
    expect(within(waiting).getByText('Nimal Silva')).toBeInTheDocument();

    const out = screen.getByText('SD2611120001').closest('a')!;
    expect(within(out).getByText('Deliver to')).toBeInTheDocument();
    expect(within(out).getByText('Out for Delivery')).toBeInTheDocument();
    expect(out).toHaveAttribute('href', '/courier/deliveries/7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01');
  });

  it('says when nothing is assigned', async () => {
    jest.spyOn(mockCourierApi, 'getToday').mockResolvedValue({
      counts: { total: 0, assigned: 0, inProgress: 0 },
      deliveries: [],
    });
    renderPage();

    expect(await screen.findByText('No deliveries assigned')).toBeInTheDocument();
  });

  it('shows an error with a retry when the list cannot be loaded', async () => {
    jest
      .spyOn(mockCourierApi, 'getToday')
      .mockRejectedValue(new ApiRequestError('Unable to reach the server.'));
    renderPage();

    expect(await screen.findByText("Couldn't load your deliveries")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try Again' })).toBeInTheDocument();
  });

  it('reloads when the app regains focus and keeps the list if that fails', async () => {
    const getToday = jest.spyOn(mockCourierApi, 'getToday');
    renderPage();
    await screen.findByText('SD2611120001');
    expect(getToday).toHaveBeenCalledTimes(1);

    getToday.mockRejectedValueOnce(new ApiRequestError('offline'));
    await act(async () => {
      fireEvent.focus(window);
    });

    expect(getToday).toHaveBeenCalledTimes(2);
    expect(await screen.findByText(/Couldn't refresh/)).toBeInTheDocument();
    expect(screen.getByText('SD2611120001')).toBeInTheDocument();
  });
});
