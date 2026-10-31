import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { clearSession } from '../utils/sessionStorage';

afterEach(() => {
  cleanup();
  clearSession(); // also forgets the in-memory access token
  window.localStorage.clear();
  window.sessionStorage.clear();
});
