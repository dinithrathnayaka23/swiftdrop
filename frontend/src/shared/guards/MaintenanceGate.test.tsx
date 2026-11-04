import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { AuthProvider } from '../../context/AuthContext';
import { SettingsContext } from '../../context/settingsContextValue';
import type { Role } from '../../types/auth';
import { DEFAULT_PUBLIC_SETTINGS } from '../../types/settings';
import { saveSession } from '../../utils/sessionStorage';
import MaintenanceGate from './MaintenanceGate';

function renderAt(path: string, maintenanceMode: boolean) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <SettingsContext.Provider
        value={{ settings: { ...DEFAULT_PUBLIC_SETTINGS, maintenanceMode }, refresh: jest.fn() }}
      >
        <AuthProvider>
          <MaintenanceGate>
            <p>the app</p>
          </MaintenanceGate>
        </AuthProvider>
      </SettingsContext.Provider>
    </MemoryRouter>,
  );
}

function signInAs(role: Role) {
  saveSession({ token: 't', user: { id: '1', displayName: 'Someone', role } }, false);
}

describe('MaintenanceGate', () => {
  it('shows the app normally', () => {
    renderAt('/customer', false);
    expect(screen.getByText('the app')).toBeInTheDocument();
  });

  it('shows the maintenance page to everyone else during maintenance', () => {
    signInAs('CUSTOMER');
    renderAt('/customer', true);

    expect(screen.getByText("We'll be back soon")).toBeInTheDocument();
    expect(screen.queryByText('the app')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'support@swiftdrop.lk' })).toHaveAttribute(
      'href',
      'mailto:support@swiftdrop.lk',
    );
  });

  it('keeps the admin sign-in reachable during maintenance', () => {
    renderAt('/admin/login', true);
    expect(screen.getByText('the app')).toBeInTheDocument();
  });

  it('lets the signed-in admin use the whole app during maintenance', () => {
    signInAs('DISPATCHER_ADMIN');
    renderAt('/', true);
    expect(screen.getByText('the app')).toBeInTheDocument();
  });
});
