import { fireEvent, render, screen } from '@testing-library/react';
import { ApiRequestError } from '../../../api/errors';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import DevSampleDeliveryButton from './DevSampleDeliveryButton';

describe('DevSampleDeliveryButton', () => {
  beforeEach(() => resetMockCourierData());
  afterEach(() => jest.restoreAllMocks());

  it('adds a sample delivery and asks the list to reload', async () => {
    const onAdded = jest.fn();
    render(<DevSampleDeliveryButton onAdded={onAdded} />);

    fireEvent.click(screen.getByRole('button', { name: 'Add sample delivery' }));

    expect(await screen.findByText(/^Added SD/)).toBeInTheDocument();
    expect(onAdded).toHaveBeenCalledTimes(1);
  });

  it('says why when the backend has no dev helper', async () => {
    jest
      .spyOn(mockCourierApi, 'addSampleDelivery')
      .mockRejectedValueOnce(new ApiRequestError('Not Found', { status: 404 }));
    const onAdded = jest.fn();
    render(<DevSampleDeliveryButton onAdded={onAdded} />);

    fireEvent.click(screen.getByRole('button', { name: 'Add sample delivery' }));

    expect(await screen.findByText("Couldn't add a sample: Not Found")).toBeInTheDocument();
    expect(onAdded).not.toHaveBeenCalled();
  });
});
