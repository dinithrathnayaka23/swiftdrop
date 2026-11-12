import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { resetToDefaults } from '../notificationStore';
import NotificationsPage from './NotificationsPage';

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/customer/notifications']}>
      <NotificationsPage />
    </MemoryRouter>,
  );
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    resetToDefaults();
  });

  it('renders page header and notification statistics', () => {
    renderPage();

    expect(screen.getByRole('heading', { level: 1, name: 'Notifications' })).toBeInTheDocument();
    expect(screen.getByText('Total received')).toBeInTheDocument();
    expect(screen.getByText('Pending attention')).toBeInTheDocument();
    expect(screen.getByText('Parcel milestones')).toBeInTheDocument();
  });

  it('displays notification cards with delivery updates', () => {
    renderPage();

    expect(screen.getByText('Parcel Out for Delivery')).toBeInTheDocument();
    expect(screen.getByText('SD2508220098')).toBeInTheDocument();
    expect(screen.getByText('Parcel in Transit to Central Hub')).toBeInTheDocument();
  });

  it('switches between Activity History and Preferences tabs', async () => {
    renderPage();

    const preferencesTab = screen.getByRole('button', { name: /Preferences & Settings/i });
    await userEvent.click(preferencesTab);

    expect(screen.getByText('Communication Channels')).toBeInTheDocument();
    expect(screen.getByText('Delivery Status Milestones')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Save Preferences/i })).toBeInTheDocument();

    const activityTab = screen.getByRole('button', { name: /Activity History/i });
    await userEvent.click(activityTab);

    expect(screen.getByText('Parcel Out for Delivery')).toBeInTheDocument();
  });

  it('filters notifications by search query', async () => {
    renderPage();

    const searchInput = screen.getByPlaceholderText(/Search tracking, keywords/i);
    await userEvent.type(searchInput, 'SD2508220098');

    expect(screen.getByText('Parcel Out for Delivery')).toBeInTheDocument();
    expect(screen.queryByText('Scheduled System Maintenance')).not.toBeInTheDocument();
  });

  it('allows marking all notifications as read', async () => {
    renderPage();

    const markAllButton = screen.getByRole('button', { name: /Mark all as read/i });
    await userEvent.click(markAllButton);

    expect(screen.queryByLabelText('Unread')).not.toBeInTheDocument();
  });
});

