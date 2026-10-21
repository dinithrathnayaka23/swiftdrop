/** The dev role switcher on the login page. Must be turned off before any real deployment. */
export function isDevLoginEnabled(): boolean {
  return import.meta.env.DEV || import.meta.env.VITE_ENABLE_DEV_LOGIN === 'true';
}
