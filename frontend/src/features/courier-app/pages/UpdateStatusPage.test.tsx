import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import UpdateStatusPage from './UpdateStatusPage';

const WAITING_FOR_PICKUP = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e04';
const IN_TRANSIT = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e02';

function renderAt(url: string) {
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/courier/deliveries/:parcelId/status" element={<UpdateStatusPage />} />
        <Route path="/courier/deliveries/:parcelId" element={<p>Delivery details page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('UpdateStatusPage', () => {
  beforeEach(() => resetMockCourierData());
  afterEach(() => jest.restoreAllMocks());

  it('records the pickup with the note and opens the delivery (mock API)', async () => {
    const update = jest.spyOn(mockCourierApi, 'updateStatus');
    renderAt(`/courier/deliveries/${WAITING_FOR_PICKUP}/status?status=PICKED_UP`);

    // The pickup stop is shown, since that is where the courier is going.
    expect(await screen.findByText('Nimal Silva')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/Note/), { target: { value: 'At the gate' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Pickup' }));

    expect(await screen.findByText('Delivery details page')).toBeInTheDocument();
    expect(update).toHaveBeenCalledWith(WAITING_FOR_PICKUP, {
      status: 'PICKED_UP',
      note: 'At the gate',
    });
    const after = await mockCourierApi.getDelivery(WAITING_FOR_PICKUP);
    expect(after?.currentStatus).toBe('PICKED_UP');
    expect(after?.timeline.at(-1)?.note).toBe('At the gate');
  });

  it('says there is nothing to do when the link asks for a step the parcel cannot take', async () => {
    renderAt(`/courier/deliveries/${IN_TRANSIT}/status?status=PICKED_UP`);

    expect(await screen.findByText('Nothing to update here')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Confirm/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open the delivery' })).toHaveAttribute(
      'href',
      `/courier/deliveries/${IN_TRANSIT}`,
    );
  });

  it('refuses DELIVERED in the link, which has its own screen', async () => {
    renderAt(`/courier/deliveries/${IN_TRANSIT}/status?status=DELIVERED`);

    expect(await screen.findByText('Nothing to update here')).toBeInTheDocument();
  });

  it('reloads and explains when someone else changed the status first (409)', async () => {
    jest.spyOn(mockCourierApi, 'updateStatus').mockImplementationOnce(async (parcelId) => {
      // Another tap or device already moved the parcel on.
      await mockCourierApi.updateStatus(parcelId, { status: 'PICKED_UP' });
      throw new ApiRequestError('A parcel that is PICKED_UP cannot be changed to PICKED_UP', {
        status: 409,
      });
    });
    renderAt(`/courier/deliveries/${WAITING_FOR_PICKUP}/status?status=PICKED_UP`);

    fireEvent.click(await screen.findByRole('button', { name: 'Confirm Pickup' }));

    expect(await screen.findByText('This delivery was just updated')).toBeInTheDocument();
    expect(screen.getByText('Picked Up', { selector: 'strong' })).toBeInTheDocument();
  });

  it('shows other errors and lets the courier try again', async () => {
    jest
      .spyOn(mockCourierApi, 'updateStatus')
      .mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
    renderAt(`/courier/deliveries/${IN_TRANSIT}/status?status=OUT_FOR_DELIVERY`);

    fireEvent.click(await screen.findByRole('button', { name: 'Confirm Out for Delivery' }));

    expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm Out for Delivery' })).toBeEnabled();
  });

  it('blocks a note longer than 500 characters', async () => {
    renderAt(`/courier/deliveries/${IN_TRANSIT}/status?status=OUT_FOR_DELIVERY`);

    fireEvent.change(await screen.findByLabelText(/Note/), {
      target: { value: 'x'.repeat(501) },
    });

    expect(screen.getByText(/Keep the note to 500 characters/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm Out for Delivery' })).toBeDisabled();
  });

  it("says when the delivery isn't the courier's", async () => {
    renderAt('/courier/deliveries/00000000-0000-0000-0000-000000000000/status?status=PICKED_UP');

    expect(await screen.findByText('Delivery not found')).toBeInTheDocument();
  });
});
