import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import FailedDeliveryPage from './FailedDeliveryPage';
import TodaysDeliveriesPage from './TodaysDeliveriesPage';

const OUT_FOR_DELIVERY = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01';
const WAITING_FOR_PICKUP = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e04';

function renderAt(parcelId: string) {
  render(
    <MemoryRouter initialEntries={[`/courier/deliveries/${parcelId}/failed`]}>
      <Routes>
        <Route path="/courier/deliveries/:parcelId/failed" element={<FailedDeliveryPage />} />
        <Route path="/courier" element={<TodaysDeliveriesPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function chooseReason(label: string) {
  fireEvent.click(await screen.findByRole('radio', { name: new RegExp(label) }));
}

describe('FailedDeliveryPage', () => {
  beforeEach(() => resetMockCourierData());
  afterEach(() => jest.restoreAllMocks());

  it('reports the failure after confirming and returns to today’s deliveries (mock API)', async () => {
    const report = jest.spyOn(mockCourierApi, 'reportFailure');
    renderAt(OUT_FOR_DELIVERY);

    expect(await screen.findByRole('link', { name: 'Call Kasun Perera' })).toBeInTheDocument();
    await chooseReason('Recipient unavailable');
    fireEvent.click(screen.getByRole('button', { name: 'Report Failed Delivery' }));

    const dialog = screen.getByRole('alertdialog');
    expect(within(dialog).getByText(/SD2611120001: Recipient unavailable/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Mark as Failed' }));

    expect(
      await screen.findByText('SD2611120001 was reported as failed (Recipient unavailable).'),
    ).toBeInTheDocument();
    expect(report).toHaveBeenCalledWith(OUT_FOR_DELIVERY, {
      reasonCode: 'RECIPIENT_UNAVAILABLE',
      note: '',
    });
    // The failed parcel has left the list.
    expect(await screen.findByText('SD2611120002')).toBeInTheDocument();
    expect(screen.queryByText('SD2611120001')).not.toBeInTheDocument();
  });

  it('asks for a reason, and for a note when the reason needs one', async () => {
    const report = jest.spyOn(mockCourierApi, 'reportFailure');
    renderAt(OUT_FOR_DELIVERY);

    fireEvent.click(await screen.findByRole('button', { name: 'Report Failed Delivery' }));
    expect(screen.getByText('Choose a reason.')).toBeInTheDocument();

    await chooseReason('Other');
    fireEvent.click(screen.getByRole('button', { name: 'Report Failed Delivery' }));
    expect(screen.getByText('Add a note to explain this reason.')).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: /Note/ }), {
      target: { value: 'Gate locked' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Report Failed Delivery' }));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    expect(report).not.toHaveBeenCalled();
  });

  it('changes nothing when the courier goes back from the confirmation', async () => {
    const report = jest.spyOn(mockCourierApi, 'reportFailure');
    renderAt(OUT_FOR_DELIVERY);

    await chooseReason('Refused by recipient');
    fireEvent.click(screen.getByRole('button', { name: 'Report Failed Delivery' }));
    fireEvent.click(screen.getByRole('button', { name: 'Go back' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(report).not.toHaveBeenCalled();
  });

  it('explains that a parcel must be picked up before it can fail', async () => {
    renderAt(WAITING_FOR_PICKUP);

    expect(await screen.findByText(/Pick up the parcel first/)).toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the delivery' })).toHaveAttribute(
      'href',
      `/courier/deliveries/${WAITING_FOR_PICKUP}`,
    );
  });

  it('reloads and explains when the delivery changed meanwhile (409)', async () => {
    jest.spyOn(mockCourierApi, 'reportFailure').mockImplementationOnce(async (parcelId) => {
      // Someone else already finished it: the delivery is no longer the courier's.
      await mockCourierApi.reportFailure(parcelId, { reasonCode: 'REFUSED' });
      throw new ApiRequestError('A parcel that is FAILED cannot be changed to FAILED', {
        status: 409,
      });
    });
    renderAt(OUT_FOR_DELIVERY);

    await chooseReason('Wrong address');
    fireEvent.click(screen.getByRole('button', { name: 'Report Failed Delivery' }));
    fireEvent.click(screen.getByRole('button', { name: 'Mark as Failed' }));

    expect(await screen.findByText('Delivery not found')).toBeInTheDocument();
  });

  it('shows other errors and keeps the form', async () => {
    jest
      .spyOn(mockCourierApi, 'reportFailure')
      .mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
    renderAt(OUT_FOR_DELIVERY);

    await chooseReason('Access issue');
    fireEvent.click(screen.getByRole('button', { name: 'Report Failed Delivery' }));
    fireEvent.click(screen.getByRole('button', { name: 'Mark as Failed' }));

    expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Access issue/ })).toBeChecked();
  });

  it("says when the delivery isn't the courier's", async () => {
    renderAt('00000000-0000-0000-0000-000000000000');

    expect(await screen.findByText('Delivery not found')).toBeInTheDocument();
  });
});
