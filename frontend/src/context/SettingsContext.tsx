import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { setMaintenanceHandler } from '../api/client';
import { settingsApi } from '../api/settings';
import { DEFAULT_PUBLIC_SETTINGS, type PublicSettings } from '../types/settings';
import { SettingsContext, type SettingsContextValue } from './settingsContextValue';

/**
 * Loads the public system settings (contact details, date/time formats, parcel limits, maintenance
 * mode) once at start-up. Pages render straight away with the defaults; if the server can't be
 * reached the defaults stay, so the site still works.
 */
export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<PublicSettings>(DEFAULT_PUBLIC_SETTINGS);

  const refresh = useCallback(async () => {
    try {
      setSettings(await settingsApi.getPublic());
    } catch {
      // Keep what we have; the next refresh may succeed.
    }
  }, []);

  useEffect(() => {
    let active = true;
    void settingsApi.getPublic().then(
      (loaded) => {
        if (active) setSettings(loaded);
      },
      () => undefined,
    );
    return () => {
      active = false;
    };
  }, []);

  // An API call refused for maintenance: show the maintenance page straight away.
  useEffect(() => {
    setMaintenanceHandler(() => setSettings((current) => ({ ...current, maintenanceMode: true })));
    return () => setMaintenanceHandler(null);
  }, []);

  const value = useMemo<SettingsContextValue>(() => ({ settings, refresh }), [settings, refresh]);
  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}
