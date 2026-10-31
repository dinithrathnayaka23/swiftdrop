// Jest stand-in for src/utils/env.ts (mapped in jest.config.js), which reads Vite's import.meta.env.
export const API_BASE_URL = '/api/v1';
export const IS_DEV = false;

export function isMockApiEnabled(): boolean {
  return true;
}
