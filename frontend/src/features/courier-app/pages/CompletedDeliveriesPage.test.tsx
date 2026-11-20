import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import CompletedDeliveriesPage from './CompletedDeliveriesPage';

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/courier/completed']}>
      <CompletedDeliveriesPage />
    </MemoryRouter>,
  );
}

describe('CompletedDeliveriesPage', () => {
  beforeAll(() => {
    URL.createObjectURL = jest.fn(() => 'blob:photo');
    URL.revokeObjectURL = jest.fn();
  });
  beforeEach(() => resetMockCourierData());
  afterEach(() => jest.restoreAllMocks());

  it('lists deliveries and failed attempts newest first (mock API)', async () => {
    renderPage();

    const cards = await screen.findAllByRole('article');
    expect(cards.map((card) => within(card).getByRole('heading').textContent)).toEqual([
      'SD2611140007',
      'SD2611140006',
      'SD2611130004',
    ]);
    expect(within(cards[0]).getByText('Received by:')).toBeInTheDocument();
    expect(within(cards[1]).getByText('Recipient unavailable')).toBeInTheDocument();
    expect(within(cards[1]).getByText('Called twice, no answer')).toBeInTheDocument();
    expect(screen.getByText('3 deliveries')).toBeInTheDocument();
  });

  it('filters by outcome from the first page', async () => {
    const getCompleted = jest.spyOn(mockCourierApi, 'getCompleted');
    renderPage();
    await screen.findAllByRole('article');

    fireEvent.change(screen.getByRole('combobox', { name: 'Outcome' }), {
      target: { value: 'FAILED_ATTEMPT' },
    });

    expect(await screen.findByText('1 delivery')).toBeInTheDocument();
    expect(getCompleted).toHaveBeenLastCalledWith(
      expect.objectContaining({ outcome: 'FAILED_ATTEMPT', page: 0 }),
    );
  });

  it('refuses an end date before the start date without asking the server', async () => {
    const getCompleted = jest.spyOn(mockCourierApi, 'getCompleted');
    renderPage();
    await screen.findAllByRole('article');
    getCompleted.mockClear();

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-11-10' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-11-01' } });

    expect(await screen.findByText('The end date is before the start date.')).toBeInTheDocument();
    expect(getCompleted).not.toHaveBeenCalledWith(expect.objectContaining({ to: '2026-11-01' }));
  });

  it('shows the proof photo, loaded with the sign-in token', async () => {
    const getPhoto = jest.spyOn(mockCourierApi, 'getProofPhoto');
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'View photo for SD2611140007' }));

    const dialog = await screen.findByRole('dialog', { name: 'SD2611140007' });
    expect(await within(dialog).findByRole('img')).toHaveAttribute('src', 'blob:photo');
    expect(getPhoto).toHaveBeenCalledWith('/proofs/mock-proof-1/photo');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Close photo' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('says when there is nothing yet', async () => {
    jest.spyOn(mockCourierApi, 'getCompleted').mockResolvedValue({
      content: [],
      page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
    });
    renderPage();

    expect(await screen.findByText('Nothing here yet')).toBeInTheDocument();
  });

  it('pages through long lists', async () => {
    const getCompleted = jest
      .spyOn(mockCourierApi, 'getCompleted')
      .mockImplementation(async ({ page }) => ({
        content: [
          {
            parcelId: `p${page}`,
            trackingNumber: `SD00000000${page}`,
            outcome: 'DELIVERED',
            completedAt: '2026-11-15T05:00:00Z',
            recipientName: 'Kasun Perera',
            dropoffArea: '12 Galle Road',
            dropoffCity: 'Colombo',
            receivedBy: 'Kasun Perera',
            failureReason: null,
            note: null,
            proofPhotoUrl: null,
          },
        ],
        page: { size: 1, number: page, totalElements: 3, totalPages: 3 },
      }));
    renderPage();

    // Wait for the first page before turning it (generous timeouts: the full suite runs in parallel).
    expect(
      await screen.findByText('SD000000000', undefined, { timeout: 5000 }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '2' }));

    expect(
      await screen.findByText('SD000000001', undefined, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(getCompleted).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
  });
});
