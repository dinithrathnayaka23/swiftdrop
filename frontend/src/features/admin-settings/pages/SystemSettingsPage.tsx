import { useCallback, useEffect, useState } from 'react';
import { settingsApi } from '../../../api/settings';
import { getErrorMessage } from '../../../api/errors';
import LoadingState from '../../../components/feedback/LoadingState';
import Alert from '../../../components/ui/Alert';
import { ErrorState } from '../../../components/feedback/States';
import type { SystemSettings } from '../../../types/settings';
import SettingsForm from '../components/SettingsForm';

type Load =
  | { state: 'loading' }
  | { state: 'error'; message: string }
  | { state: 'ready'; settings: SystemSettings };

/** Admin: System Settings. */
export default function SystemSettingsPage() {
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [saved, setSaved] = useState(false);

  const fetchSettings = useCallback(() => {
    return settingsApi.get().then(
      (settings) => setLoad({ state: 'ready', settings }),
      (err: unknown) => setLoad({ state: 'error', message: getErrorMessage(err) }),
    );
  }, []);

  useEffect(() => {
    let active = true;
    settingsApi.get().then(
      (settings) => {
        if (active) setLoad({ state: 'ready', settings });
      },
      (err: unknown) => {
        if (active) setLoad({ state: 'error', message: getErrorMessage(err) });
      },
    );
    return () => {
      active = false;
    };
  }, []);

  return (
    <section>
      <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
        System Settings
      </h1>
      <p className="mt-1 text-slate-500">
        Configure application settings, notifications and operational preferences.
      </p>

      <div className="mt-6">
        {saved && load.state === 'ready' && (
          <Alert tone="success" className="mb-4">
            Settings saved. Changes apply across SwiftDrop straight away.
          </Alert>
        )}
        {load.state === 'loading' && <LoadingState title="Loading settings..." />}
        {load.state === 'error' && (
          <div className="rounded-2xl border border-slate-200 bg-white">
            <ErrorState
              title="Couldn't load the settings"
              message={load.message}
              onAction={() => {
                setLoad({ state: 'loading' });
                void fetchSettings();
              }}
            />
          </div>
        )}
        {load.state === 'ready' && (
          // A new version (after saving or reloading) starts a fresh form.
          <SettingsForm
            key={load.settings.version}
            initial={load.settings}
            onSaved={(settings) => {
              setSaved(true);
              setLoad({ state: 'ready', settings });
            }}
            onReload={() => {
              setSaved(false);
              setLoad({ state: 'loading' });
              void fetchSettings();
            }}
          />
        )}
      </div>
    </section>
  );
}
