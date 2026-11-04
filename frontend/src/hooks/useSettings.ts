import { useContext } from 'react';
import { SettingsContext, type SettingsContextValue } from '../context/settingsContextValue';

/** The public system settings (contact details, formats, parcel limits, maintenance mode). */
export function useSettings(): SettingsContextValue {
  return useContext(SettingsContext);
}
