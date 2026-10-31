// Jest stand-in for react-router's lib/dom/ssr/routeModules.js (mapped in jest.config.js).
// Only React Router's framework mode loads route modules; this app never does.
export function loadRouteModule(): Promise<never> {
  return Promise.reject(new Error('Route modules are not used in tests'));
}
