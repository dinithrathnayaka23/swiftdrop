import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import ProofOfDeliveryPage from './ProofOfDeliveryPage';
import TodaysDeliveriesPage from './TodaysDeliveriesPage';

// jsdom has no canvas: the resized photo is a small stand-in blob.
const mockResized = new Blob(['resized'], { type: 'image/jpeg' });
jest.mock('../photo', () => ({ resizePhoto: jest.fn(async () => mockResized) }));

const OUT_FOR_DELIVERY = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e01';
const IN_TRANSIT = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e02';

function renderAt(parcelId: string) {
  render(
    <MemoryRouter initialEntries={[`/courier/deliveries/${parcelId}/proof`]}>
      <Routes>
        <Route path="/courier/deliveries/:parcelId/proof" element={<ProofOfDeliveryPage />} />
        <Route path="/courier" element={<TodaysDeliveriesPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function takePhoto() {
  const input = await screen.findByLabelText('Photo of the delivered parcel');
  fireEvent.change(input, {
    target: { files: [new File(['camera'], 'IMG_0001.jpg', { type: 'image/jpeg' })] },
  });
  return screen.findByRole('img', { name: 'Proof of delivery' });
}

describe('ProofOfDeliveryPage', () => {
  beforeAll(() => {
    URL.createObjectURL = jest.fn(() => 'blob:preview');
    URL.revokeObjectURL = jest.fn();
  });
  beforeEach(() => resetMockCourierData());
  afterEach(() => jest.restoreAllMocks());

  it('confirms the delivery with the name, the resized photo and the note (mock API)', async () => {
    const deliver = jest.spyOn(mockCourierApi, 'deliver');
    renderAt(OUT_FOR_DELIVERY);

    // The recipient's name is filled in and can be changed.
    const name = await screen.findByRole('textbox', { name: /Received by/ });
    expect(name).toHaveValue('Kasun Perera');
    fireEvent.change(name, { target: { value: 'Nimali (sister)' } });
    expect(await takePhoto()).toHaveAttribute('src', 'blob:preview');
    fireEvent.change(screen.getByRole('textbox', { name: /Note/ }), {
      target: { value: 'At the door' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Delivery' }));

    expect(await screen.findByText('SD2611120001 was delivered.')).toBeInTheDocument();
    expect(deliver).toHaveBeenCalledWith(OUT_FOR_DELIVERY, {
      recipientName: 'Nimali (sister)',
      note: 'At the door',
      photo: mockResized,
    });
    expect(screen.queryByText('SD2611120001')).not.toBeInTheDocument();
  });

  it('delivers without a photo, and a photo can be removed again', async () => {
    const deliver = jest.spyOn(mockCourierApi, 'deliver');
    renderAt(OUT_FOR_DELIVERY);

    await takePhoto();
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(screen.queryByRole('img', { name: 'Proof of delivery' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Delivery' }));

    // Wait until the delivery is done, so it can't finish during the next test.
    expect(await screen.findByText('SD2611120001 was delivered.')).toBeInTheDocument();
    expect(deliver).toHaveBeenCalledWith(
      OUT_FOR_DELIVERY,
      expect.objectContaining({ photo: undefined }),
    );
  });

  it('asks for the name of the person who received it', async () => {
    const deliver = jest.spyOn(mockCourierApi, 'deliver');
    renderAt(OUT_FOR_DELIVERY);

    fireEvent.change(await screen.findByRole('textbox', { name: /Received by/ }), {
      target: { value: '   ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Delivery' }));

    expect(screen.getByText('Enter who received the parcel.')).toBeInTheDocument();
    expect(deliver).not.toHaveBeenCalled();
  });

  it('explains a photo that is too large (413)', async () => {
    jest
      .spyOn(mockCourierApi, 'deliver')
      .mockRejectedValueOnce(new ApiRequestError('Payload Too Large', { status: 413 }));
    renderAt(OUT_FOR_DELIVERY);

    await takePhoto();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Delivery' }));

    expect(await screen.findByText(/The photo is too large/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm Delivery' })).toBeEnabled();
  });

  it('reloads and explains when the delivery changed meanwhile (409)', async () => {
    jest.spyOn(mockCourierApi, 'deliver').mockImplementationOnce(async (parcelId) => {
      // Delivered from another tap: the delivery is no longer the courier's.
      await mockCourierApi.deliver(parcelId, { recipientName: 'Kasun Perera' });
      throw new ApiRequestError('A parcel that is DELIVERED cannot be changed to DELIVERED', {
        status: 409,
      });
    });
    renderAt(OUT_FOR_DELIVERY);

    fireEvent.click(await screen.findByRole('button', { name: 'Confirm Delivery' }));

    expect(await screen.findByText('Delivery not found')).toBeInTheDocument();
  });

  it('says to go out for delivery first', async () => {
    renderAt(IN_TRANSIT);

    expect(await screen.findByText(/Mark it out for delivery first/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirm Delivery' })).not.toBeInTheDocument();
  });

  it("says when the delivery isn't the courier's", async () => {
    renderAt('00000000-0000-0000-0000-000000000000');

    expect(await screen.findByText('Delivery not found')).toBeInTheDocument();
  });
});
