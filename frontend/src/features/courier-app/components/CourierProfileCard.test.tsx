import { fireEvent, render, screen, within } from '@testing-library/react';
import { ApiRequestError } from '../../../api/errors';
import { mockCourierApi, resetMockCourierData } from '../mock/mockCourierApi';
import CourierProfileCard from './CourierProfileCard';

describe('CourierProfileCard', () => {
  beforeEach(() => resetMockCourierData());
  afterEach(() => jest.restoreAllMocks());

  it('shows who the courier is, their vehicle and their areas (mock API)', async () => {
    render(<CourierProfileCard />);

    const card = await screen.findByRole('region', { name: 'Your profile' });
    expect(within(card).getByText('Ramesh Perera')).toBeInTheDocument();
    expect(within(card).getByText('WP CAB 1234')).toBeInTheDocument();
    expect(within(card).getByText('Colombo 01-07, Colombo Suburbs')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Online' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('switches the courier on a break and back online', async () => {
    const change = jest.spyOn(mockCourierApi, 'changeStatus');
    render(<CourierProfileCard />);

    fireEvent.click(await screen.findByRole('button', { name: 'On break' }));

    expect(await screen.findByText(/get no new ones/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'On break' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(change).toHaveBeenCalledWith('ON_BREAK');

    fireEvent.click(screen.getByRole('button', { name: 'Online' }));
    expect(await screen.findByText('You can be given new deliveries.')).toBeInTheDocument();
  });

  it('explains when the change is refused and keeps the old status', async () => {
    jest
      .spyOn(mockCourierApi, 'changeStatus')
      .mockRejectedValueOnce(
        new ApiRequestError('This courier is deactivated and cannot go online', { status: 409 }),
      );
    render(<CourierProfileCard />);

    fireEvent.click(await screen.findByRole('button', { name: 'Offline' }));

    expect(
      await screen.findByText('This courier is deactivated and cannot go online'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Online' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('warns a deactivated courier', async () => {
    const profile = await mockCourierApi.getProfile();
    jest.spyOn(mockCourierApi, 'getProfile').mockResolvedValue({ ...profile, active: false });
    render(<CourierProfileCard />);

    expect(await screen.findByText(/account is deactivated/)).toBeInTheDocument();
  });

  it("doesn't block the page when the profile can't load", async () => {
    jest
      .spyOn(mockCourierApi, 'getProfile')
      .mockRejectedValue(new ApiRequestError('Unable to reach the server.'));
    render(<CourierProfileCard />);

    expect(await screen.findByText("Couldn't load your profile")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
