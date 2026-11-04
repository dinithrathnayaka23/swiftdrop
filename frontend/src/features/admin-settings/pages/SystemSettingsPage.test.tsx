import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, Link, RouterProvider } from 'react-router';
import { settingsApi } from '../../../api/settings';
import { ApiRequestError } from '../../../api/errors';
import type { SystemSettings } from '../../../types/settings';
import SystemSettingsPage from './SystemSettingsPage';

jest.mock('../../../api/settings', () => ({
  settingsApi: { get: jest.fn(), update: jest.fn(), getPublic: jest.fn() },
}));

const getSettings = jest.mocked(settingsApi.get);
const updateSettings = jest.mocked(settingsApi.update);

const SETTINGS: SystemSettings = {
  companyName: 'SwiftDrop',
  contactEmail: 'support@swiftdrop.lk',
  contactPhone: null,
  companyAddress: null,
  timeZone: 'Asia/Colombo',
  dateFormat: 'DD_MMM_YYYY',
  timeFormat: 'H12',
  itemsPerPage: 10,
  defaultParcelType: 'PARCEL',
  maxParcelWeightKg: 50,
  maxParcelDimensionCm: 120,
  failedDeliveryAction: 'RETRY_DELIVERY',
  autoAssignCourier: false,
  notifyFailedDeliveries: true,
  notifyNewMerchants: true,
  notifyCourierIssues: true,
  dailySummaryReport: false,
  maintenanceMode: false,
  version: 4,
  // 05:50 UTC = 11:20 AM in Colombo
  updatedAt: '2025-08-24T05:50:00Z',
};

function renderPage() {
  const router = createMemoryRouter(
    [
      {
        path: '/admin/settings',
        element: (
          <>
            <Link to="/admin">Overview</Link>
            <SystemSettingsPage />
          </>
        ),
      },
      { path: '/admin', element: <p>Overview page</p> },
    ],
    { initialEntries: ['/admin/settings'] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

async function renderLoaded() {
  getSettings.mockResolvedValue(SETTINGS);
  const router = renderPage();
  await screen.findByRole('tab', { name: /General/ });
  return router;
}

function saveButton() {
  return screen.getByRole('button', { name: 'Save Changes' });
}

describe('SystemSettingsPage', () => {
  beforeEach(() => {
    getSettings.mockReset();
    updateSettings.mockReset();
  });

  it('shows the saved settings, when they were saved, and nothing to save yet', async () => {
    await renderLoaded();

    expect(screen.getByRole('heading', { name: 'System Settings' })).toBeInTheDocument();
    expect(screen.getByText('Last saved 24 Aug 2025, 11:20 AM')).toBeInTheDocument();
    expect(screen.getByLabelText('Company Name')).toHaveValue('SwiftDrop');
    expect(screen.getByLabelText('Default Timezone')).toHaveValue('Asia/Colombo');
    expect(saveButton()).toBeDisabled();
  });

  it('saves the changed form with the version it was loaded with', async () => {
    await renderLoaded();
    updateSettings.mockResolvedValue({ ...SETTINGS, companyName: 'SwiftDrop Lanka', version: 5 });

    const name = screen.getByLabelText('Company Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'SwiftDrop Lanka');
    await userEvent.click(screen.getByRole('tab', { name: /Notifications/ }));
    await userEvent.click(screen.getByLabelText('Daily operational summary report'));
    await userEvent.click(saveButton());

    expect(updateSettings).toHaveBeenCalledWith(
      expect.objectContaining({
        version: 4,
        companyName: 'SwiftDrop Lanka',
        dailySummaryReport: true,
        contactPhone: null,
      }),
    );
    expect(await screen.findByText(/Settings saved/)).toBeInTheDocument();
    expect(saveButton()).toBeDisabled(); // fresh form for version 5
  });

  it('opens the tab with the mistake and does not save', async () => {
    await renderLoaded();
    await userEvent.click(screen.getByRole('tab', { name: /Parcel Settings/ }));
    const weight = screen.getByLabelText('Maximum Parcel Weight (kg)');
    await userEvent.clear(weight);
    await userEvent.type(weight, '0');
    await userEvent.click(screen.getByRole('tab', { name: /General/ }));

    await userEvent.click(saveButton());

    expect(screen.getByRole('tab', { name: /Parcel Settings/ })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByText('Maximum parcel weight must be more than 0')).toBeInTheDocument();
    expect(screen.getByLabelText('Maximum Parcel Weight (kg)')).toHaveFocus();
    expect(updateSettings).not.toHaveBeenCalled();
  });

  it('asks before turning on maintenance mode', async () => {
    await renderLoaded();
    await userEvent.click(screen.getByRole('tab', { name: /Maintenance/ }));
    const toggle = screen.getByRole('switch', { name: 'Maintenance Mode' });

    await userEvent.click(toggle);
    const dialog = screen.getByRole('alertdialog', { name: 'Turn on maintenance mode?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(toggle).toHaveAttribute('aria-checked', 'false');

    await userEvent.click(toggle);
    await userEvent.click(screen.getByRole('button', { name: 'Turn On' }));
    expect(toggle).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByText('Save the settings to start maintenance.')).toBeInTheDocument();
  });

  it('explains when someone else saved first and reloads on request', async () => {
    await renderLoaded();
    updateSettings.mockRejectedValue(
      new ApiRequestError('changed', { status: 409, code: 'CONFLICT' }),
    );
    await userEvent.type(screen.getByLabelText('Company Name'), ' Ltd');

    await userEvent.click(saveButton());

    expect(
      await screen.findByText('These settings were changed somewhere else'),
    ).toBeInTheDocument();
    getSettings.mockResolvedValue({ ...SETTINGS, companyName: 'Their change', version: 5 });
    await userEvent.click(screen.getByRole('button', { name: 'Reload Settings' }));
    expect(await screen.findByDisplayValue('Their change')).toBeInTheDocument();
  });

  it("puts the server's field errors on the right field and tab", async () => {
    await renderLoaded();
    updateSettings.mockRejectedValue(
      new ApiRequestError('Choose a valid time zone', {
        status: 400,
        code: 'VALIDATION_FAILED',
        fieldErrors: [{ field: 'timeZone', message: 'Choose a valid time zone' }],
      }),
    );
    await userEvent.click(screen.getByRole('tab', { name: /Notifications/ }));
    await userEvent.click(screen.getByLabelText('Daily operational summary report'));

    await userEvent.click(saveButton());

    expect(await screen.findByText('Choose a valid time zone')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /General/ })).toHaveAttribute('aria-selected', 'true');
  });

  it('offers to try again when the settings cannot be loaded', async () => {
    getSettings.mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
    renderPage();

    expect(await screen.findByText("Couldn't load the settings")).toBeInTheDocument();
    getSettings.mockResolvedValue(SETTINGS);
    await userEvent.click(screen.getByRole('button', { name: 'Try Again' }));

    expect(await screen.findByLabelText('Company Name')).toHaveValue('SwiftDrop');
  });

  it('asks before leaving with unsaved changes, and stays on request', async () => {
    const router = await renderLoaded();
    await userEvent.type(screen.getByLabelText('Company Name'), ' Ltd');

    await userEvent.click(screen.getByRole('link', { name: 'Overview' }));
    const dialog = screen.getByRole('alertdialog', { name: 'Leave without saving?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Stay' }));

    expect(router.state.location.pathname).toBe('/admin/settings');
    expect(screen.getByLabelText('Company Name')).toHaveValue('SwiftDrop Ltd');

    await userEvent.click(screen.getByRole('link', { name: 'Overview' }));
    await userEvent.click(screen.getByRole('button', { name: 'Leave Page' }));
    expect(await screen.findByText('Overview page')).toBeInTheDocument();
  });

  it('leaves without asking when nothing has changed', async () => {
    await renderLoaded();

    await userEvent.click(screen.getByRole('link', { name: 'Overview' }));

    expect(await screen.findByText('Overview page')).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('never holds up a redirect such as signing out', async () => {
    const router = await renderLoaded();
    await userEvent.type(screen.getByLabelText('Company Name'), ' Ltd');

    await router.navigate('/admin', { replace: true });

    expect(await screen.findByText('Overview page')).toBeInTheDocument();
  });

  it('asks the browser to confirm closing the tab only with unsaved changes', async () => {
    await renderLoaded();
    expect(fireEvent(window, new Event('beforeunload', { cancelable: true }))).toBe(true);

    await userEvent.type(screen.getByLabelText('Company Name'), ' Ltd');

    expect(fireEvent(window, new Event('beforeunload', { cancelable: true }))).toBe(false);
  });
});
