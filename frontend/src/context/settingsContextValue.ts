import { createContext } from 'react';
import { DEFAULT_PUBLIC_SETTINGS, type PublicSettings } from '../types/settings';

export interface SettingsContextValue {
  /** The backend's defaults until the real settings have loaded. */
  settings: PublicSettings;
  /** Reads the settings again (after the admin saves, or when maintenance starts). */
  refresh: () => Promise<void>;
}

/** Outside <SettingsProvider> (e.g. in a component test) the defaults apply. */
export const SettingsContext = createContext<SettingsContextValue>({
  settings: DEFAULT_PUBLIC_SETTINGS,
  refresh: async () => undefined,
});
