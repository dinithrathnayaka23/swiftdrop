import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { resetToDefaults } from '../notifications/notificationStore';
import NotificationBell from './NotificationBell';

// Mock useAuth to simulate a logged-in customer
jest.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 'test-user-1', displayName: 'Jane Doe', role: 'CUSTOMER' },
    isAuthenticated: true,
  }),
}));

function renderBell() {
  render(
    <MemoryRouter>
      <NotificationBell />
    </MemoryRouter>,
  );
}

describe('NotificationBell', () => {
  beforeEach(() => {
    localStorage.clear();
    resetToDefaults();
  });

  it('renders bell button with unread badge counter', () => {
    renderBell();

    const button = screen.getByRole('button', { name: /Notifications/i });
    expect(button).toBeInTheDocument();
    // Default initial notifications have 3 unread
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('opens notification dropdown on click', async () => {
    renderBell();

    const button = screen.getByRole('button', { name: /Notifications/i });
    await userEvent.click(button);

    expect(screen.getByRole('dialog', { name: 'Notification center' })).toBeInTheDocument();
    expect(screen.getByText('View all notifications')).toBeInTheDocument();
    expect(screen.getByText('Parcel Out for Delivery')).toBeInTheDocument();
  });

  it('marks all as read when clicking mark all read button inside dropdown', async () => {
    renderBell();

    const button = screen.getByRole('button', { name: /Notifications/i });
    await userEvent.click(button);

    const markAllButton = screen.getByRole('button', { name: /Mark all read/i });
    await userEvent.click(markAllButton);

    expect(screen.queryByText('3')).not.toBeInTheDocument();
  });
});

