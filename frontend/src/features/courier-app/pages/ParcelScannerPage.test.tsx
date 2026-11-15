import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { ApiRequestError } from '../../../api/errors';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import ParcelScannerPage from './ParcelScannerPage';

const PICKED_UP = '7b0c1a52-4d6e-4f1a-9c3b-1a2b3c4d5e03';

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/courier/scan']}>
      <Routes>
        <Route path="/courier/scan" element={<ParcelScannerPage />} />
        <Route path="/courier/deliveries/:parcelId" element={<p>Delivery details page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

function typeAndFind(value: string) {
  fireEvent.change(screen.getByRole('textbox', { name: 'Tracking number' }), {
    target: { value },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Find Delivery' }));
}

describe('ParcelScannerPage', () => {
  beforeEach(() => resetMockCourierData());
  afterEach(() => jest.restoreAllMocks());

  describe('without a camera scanner (typed numbers)', () => {
    it('says scanning is not available and opens a typed delivery', async () => {
      const find = jest.spyOn(mockCourierApi, 'findByTrackingNumber');
      renderPage();

      expect(screen.getByText("Scanning isn't available in this browser")).toBeInTheDocument();
      typeAndFind('sd 2611 1200 03');

      expect(await screen.findByText('Delivery details page')).toBeInTheDocument();
      expect(find).toHaveBeenCalledWith('SD2611120003');
    });

    it("says when the number isn't one of the courier's deliveries", async () => {
      renderPage();

      typeAndFind('SD0000000000');

      expect(await screen.findByText('Not one of your deliveries')).toBeInTheDocument();
      expect(screen.getByText(/SD0000000000 isn't assigned to you/)).toBeInTheDocument();
    });

    it('shows a failed look-up', async () => {
      jest
        .spyOn(mockCourierApi, 'findByTrackingNumber')
        .mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
      renderPage();

      typeAndFind('SD2611120003');

      expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
    });

    it('only lets the courier search once something is typed', () => {
      renderPage();

      expect(screen.getByRole('button', { name: 'Find Delivery' })).toBeDisabled();
    });
  });

  describe('with a camera scanner', () => {
    const stopTrack = jest.fn();
    let detected: string[] = [];

    beforeEach(() => {
      detected = ['SD2611120003'];
      stopTrack.mockClear();
      (globalThis as { BarcodeDetector?: unknown }).BarcodeDetector = class {
        static async getSupportedFormats() {
          return ['qr_code', 'code_128'];
        }
        async detect() {
          const value = detected.shift();
          return value ? [{ rawValue: value, format: 'code_128' }] : [];
        }
      };
      Object.defineProperty(navigator, 'mediaDevices', {
        configurable: true,
        value: {
          getUserMedia: jest.fn(async () => ({ getTracks: () => [{ stop: stopTrack }] })),
        },
      });
      jest.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    });

    afterEach(() => {
      delete (globalThis as { BarcodeDetector?: unknown }).BarcodeDetector;
      Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
    });

    it('opens the scanned delivery and switches the camera off', async () => {
      renderPage();

      expect(screen.getByLabelText('Camera view')).toBeInTheDocument();
      expect(await screen.findByText('Delivery details page')).toBeInTheDocument();
      expect(stopTrack).toHaveBeenCalled();
      expect(screen.queryByText(PICKED_UP)).not.toBeInTheDocument();
    });

    it('explains a blocked camera and still offers typing', async () => {
      (navigator.mediaDevices.getUserMedia as jest.Mock).mockRejectedValueOnce(
        new DOMException('denied', 'NotAllowedError'),
      );
      renderPage();

      expect(await screen.findByText(/Camera access is blocked/)).toBeInTheDocument();
      expect(screen.getByRole('textbox', { name: 'Tracking number' })).toBeInTheDocument();
    });

    it('offers to scan again after a code that is not the courier’s', async () => {
      detected = ['SD0000000000'];
      renderPage();

      expect(await screen.findByText('Not one of your deliveries')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Scan again' }));

      expect(screen.queryByText('Not one of your deliveries')).not.toBeInTheDocument();
    });
  });
});
