import '@testing-library/jest-dom';
import { cleanup, configure } from '@testing-library/react';
import { clearSession } from '../utils/sessionStorage';

// Routes are lazy-loaded, so the first visit to a page compiles it on demand. That can take
// longer than the default 1s on slower CI runners; wait up to 5s (well under testTimeout).
configure({ asyncUtilTimeout: 5000 });

afterEach(() => {
  cleanup();
  clearSession(); // also forgets the in-memory access token
  window.localStorage.clear();
  window.sessionStorage.clear();
});
