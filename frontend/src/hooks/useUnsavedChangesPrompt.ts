import { useEffect } from 'react';
import { useBlocker, type Blocker } from 'react-router';

/**
 * Asks before leaving a page with unsaved changes. Closing or reloading the tab gets the browser's
 * own "Leave site?" prompt; leaving within the app (links, back/forward) is held in the returned
 * blocker until the page asks the user and calls `proceed()` or `reset()`.
 *
 * Redirects the app makes itself (sign out, session expired, access checks) all use `replace` and
 * are never held: the user didn't choose them and the page can't stay anyway.
 */
export function useUnsavedChangesPrompt(dirty: boolean): Blocker {
  const blocker = useBlocker(
    ({ currentLocation, nextLocation, historyAction }) =>
      dirty && historyAction !== 'REPLACE' && currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = ''; // older browsers only show the prompt with this set
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [dirty]);

  return blocker;
}
