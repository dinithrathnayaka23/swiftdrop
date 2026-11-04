import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { authApi } from '../api/auth';
import { AuthProvider } from '../context/AuthContext';
import type { Role } from '../types/auth';
import { saveSession } from '../utils/sessionStorage';
import AppRoutes from './AppRoutes';

jest.mock('../api/auth', () => ({
  authApi: { logout: jest.fn(async () => undefined) },
}));

function signInAs(role: Role, displayName = 'Ayesha Khan') {
  saveSession({ token: 't', user: { id: '1', displayName, role } }, false);
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('AppRoutes', () => {
  it('opens the customer dashboard inside the customer layout', async () => {
    signInAs('CUSTOMER');
    renderAt('/customer');

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    const menu = screen.getAllByRole('navigation', { name: 'Main navigation' })[0];
    for (const label of ['Book a Delivery', 'My Deliveries', 'Notifications', 'Profile']) {
      expect(within(menu).getByRole('link', { name: label })).toBeInTheDocument();
    }
    expect(screen.getByRole('link', { name: /Contact Support/ })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Account menu for Ayesha Khan' }),
    ).toBeInTheDocument();
  });

  it('shows the admin menu in collapsible groups', async () => {
    signInAs('DISPATCHER_ADMIN', 'Admin');
    renderAt('/admin');

    expect(await screen.findByRole('heading', { name: 'Operations Overview' })).toBeInTheDocument();
    const reports = screen.getByRole('button', { name: 'Reports' });
    expect(screen.getByRole('link', { name: 'Operational Reports' })).toBeInTheDocument();

    await userEvent.click(reports);

    expect(reports).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('link', { name: 'Operational Reports' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Contact Support/ })).not.toBeInTheDocument();
  });

  it('moves between screens from the menu', async () => {
    signInAs('MERCHANT', 'BrightMart');
    renderAt('/merchant');
    await screen.findByRole('heading', { name: 'Dashboard' });

    await userEvent.click(screen.getByRole('link', { name: 'API Integration' }));

    expect(await screen.findByRole('heading', { name: 'API Integration' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'API Integration' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('shows the 404 page inside the layout for an unknown screen', async () => {
    signInAs('CUSTOMER');
    renderAt('/customer/does-not-exist');

    expect(await screen.findByText('Page Not Found')).toBeInTheDocument();
    expect(screen.getAllByRole('navigation', { name: 'Main navigation' })[0]).toBeInTheDocument();
  });

  it('shows the courier app with its bottom tab bar', async () => {
    signInAs('COURIER', 'Amal Kumara');
    renderAt('/courier');

    expect(await screen.findByRole('heading', { name: "Today's Deliveries" })).toBeInTheDocument();
    const tabs = screen.getByRole('navigation', { name: 'Courier app' });
    for (const label of ["Today's Deliveries", 'Scan Parcel', 'Completed', 'Account']) {
      expect(within(tabs).getByRole('link', { name: label })).toBeInTheDocument();
    }
  });

  it('sends signed-out visitors to the sign-in page of the area they opened', async () => {
    renderAt('/admin/users');
    expect(await screen.findByRole('heading', { name: 'Admin Portal' })).toBeInTheDocument();
  });

  it('refuses another role with the 403 page', async () => {
    signInAs('CUSTOMER');
    renderAt('/admin');
    expect(await screen.findByText('Access Denied')).toBeInTheDocument();
  });

  it('signs out from the account menu and returns staff to their sign-in page', async () => {
    signInAs('DISPATCHER_ADMIN', 'Admin');
    renderAt('/admin');
    await screen.findByRole('heading', { name: 'Operations Overview' });

    await userEvent.click(screen.getByRole('button', { name: 'Account menu for Admin' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Admin Portal' })).toBeInTheDocument();
    expect(authApi.logout).toHaveBeenCalled();
  });

  it('opens the menu drawer on small screens and closes it with Escape', async () => {
    signInAs('CUSTOMER');
    renderAt('/customer');
    await screen.findByRole('heading', { name: 'Dashboard' });

    await userEvent.click(screen.getByRole('button', { name: 'Open menu' }));
    expect(screen.getByRole('dialog', { name: 'Menu' })).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument();
  });
});
