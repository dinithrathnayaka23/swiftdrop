import { useMemo } from 'react';
import { formatDate, formatDateTime, formatShortDateTime, type DateDisplay } from '../utils/date';
import { useSettings } from './useSettings';

/** Date formatters that follow the admin's time zone, date format and time format. */
export function useDateFormat() {
  const { settings } = useSettings();
  const { timeZone, dateFormat, timeFormat } = settings;
  return useMemo(() => {
    const display: DateDisplay = { timeZone, dateFormat, timeFormat };
    return {
      formatDateTime: (iso: string) => formatDateTime(iso, display),
      formatShortDateTime: (iso: string) => formatShortDateTime(iso, display),
      formatDate: (iso: string) => formatDate(iso, display),
    };
  }, [timeZone, dateFormat, timeFormat]);
}
