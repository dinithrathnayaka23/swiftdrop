import {
  Bell,
  CheckCircle2,
  Mail,
  MessageSquare,
  RotateCcw,
  Save,
  ShieldAlert,
  Truck,
} from 'lucide-react';
import { useState } from 'react';
import Button from '../../../components/ui/Button';
import Switch from '../../../components/ui/Switch';
import type { NotificationPreferences } from '../types';

interface NotificationPreferencesTabProps {
  preferences: NotificationPreferences;
  onSave: (preferences: NotificationPreferences) => void;
  onReset: () => void;
}

export default function NotificationPreferencesTab({
  preferences: initialPreferences,
  onSave,
  onReset,
}: NotificationPreferencesTabProps) {
  const [form, setForm] = useState<NotificationPreferences>(initialPreferences);
  const [savedSuccess, setSavedSuccess] = useState(false);

  function handleSave(e: React.FormEvent) {
    e.preventDefault();
    onSave(form);
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 4000);
  }

  function handleReset() {
    onReset();
    setSavedSuccess(false);
  }

  return (
    <form onSubmit={handleSave} className="space-y-8">
      {/* Success banner */}
      {savedSuccess && (
        <div className="flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <CheckCircle2 className="size-5 shrink-0 text-emerald-600" />
          <span>Your notification preferences have been successfully updated!</span>
        </div>
      )}

      {/* Section 1: Communication Channels */}
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
        <div className="border-b border-slate-100 pb-4">
          <h2 className="text-base font-semibold text-slate-900">Communication Channels</h2>
          <p className="mt-0.5 text-sm text-slate-500">
            Choose which platforms and services SwiftDrop can use to notify you about your parcels.
          </p>
        </div>

        <div className="mt-6 divide-y divide-slate-100">
          {/* Email */}
          <div className="flex flex-col gap-4 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3.5">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
                <Mail className="size-5" />
              </span>
              <div>
                <h3 className="text-sm font-semibold text-slate-900">Email Notifications</h3>
                <p className="text-xs text-slate-500 leading-relaxed">
                  Receive detailed tracking summaries, invoices, and delivery receipt PDFs.
                </p>
                <span className="mt-1 inline-block text-xs font-mono text-slate-400">
                  {form.notificationEmail || 'Active account email'}
                </span>
              </div>
            </div>
            <div className="sm:shrink-0">
              <Switch
                label="Email Alerts"
                checked={form.emailEnabled}
                onChange={(checked) => setForm((prev) => ({ ...prev, emailEnabled: checked }))}
              />
            </div>
          </div>

          {/* SMS */}
          <div className="flex flex-col gap-4 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3.5">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
                <MessageSquare className="size-5" />
              </span>
              <div>
                <h3 className="text-sm font-semibold text-slate-900">SMS / Text Alerts</h3>
                <p className="text-xs text-slate-500 leading-relaxed">
                  Real-time SMS when your courier is en route and when your package is dropped off.
                </p>
                <span className="mt-1 inline-block text-xs font-mono text-slate-400">
                  {form.notificationPhone || 'Active mobile number'}
                </span>
              </div>
            </div>
            <div className="sm:shrink-0">
              <Switch
                label="SMS Alerts"
                checked={form.smsEnabled}
                onChange={(checked) => setForm((prev) => ({ ...prev, smsEnabled: checked }))}
              />
            </div>
          </div>

          {/* In-App */}
          <div className="flex flex-col gap-4 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3.5">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                <Bell className="size-5" />
              </span>
              <div>
                <h3 className="text-sm font-semibold text-slate-900">In-App Live Alerts</h3>
                <p className="text-xs text-slate-500 leading-relaxed">
                  Instant live badge updates and popovers while logged in to the customer dashboard.
                </p>
              </div>
            </div>
            <div className="sm:shrink-0">
              <Switch
                label="In-App Alerts"
                checked={form.inAppEnabled}
                onChange={(checked) => setForm((prev) => ({ ...prev, inAppEnabled: checked }))}
              />
            </div>
          </div>
        </div>
      </section>

      {/* Section 2: Parcel Lifecycle Milestones */}
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
        <div className="border-b border-slate-100 pb-4">
          <div className="flex items-center gap-2">
            <Truck className="size-5 text-brand-700" />
            <h2 className="text-base font-semibold text-slate-900">Delivery Status Milestones</h2>
          </div>
          <p className="mt-0.5 text-sm text-slate-500">
            Select the exact transit stages that should generate automatic notifications.
          </p>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-5 md:grid-cols-2">
          {/* Picked up */}
          <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-4">
            <Switch
              label="Parcel Picked Up"
              description="Notify when the courier collects the parcel from the pickup address."
              checked={form.notifyOnPickedUp}
              onChange={(checked) => setForm((prev) => ({ ...prev, notifyOnPickedUp: checked }))}
            />
          </div>

          {/* In transit */}
          <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-4">
            <Switch
              label="In Transit (Hub Departures)"
              description="Notify when parcels move between regional sorting facilities."
              checked={form.notifyOnInTransit}
              onChange={(checked) => setForm((prev) => ({ ...prev, notifyOnInTransit: checked }))}
            />
          </div>

          {/* Out for delivery */}
          <div className="rounded-xl border border-brand-100 bg-brand-50/20 p-4">
            <Switch
              label="Out for Delivery (Recommended)"
              description="Receive driver details and estimated arrival time."
              checked={form.notifyOnOutForDelivery}
              onChange={(checked) =>
                setForm((prev) => ({ ...prev, notifyOnOutForDelivery: checked }))
              }
            />
          </div>

          {/* Delivered */}
          <div className="rounded-xl border border-brand-100 bg-brand-50/20 p-4">
            <Switch
              label="Delivered (Recommended)"
              description="Instant delivery confirmation with recipient signature and photo proof."
              checked={form.notifyOnDelivered}
              onChange={(checked) => setForm((prev) => ({ ...prev, notifyOnDelivered: checked }))}
            />
          </div>

          {/* Exceptions */}
          <div className="col-span-1 rounded-xl border border-red-100 bg-red-50/20 p-4 md:col-span-2">
            <Switch
              label="Delivery Exceptions & Delays"
              description="Urgent notice if an attempt fails, address needs clarification, or weather causes a hold."
              tone="danger"
              checked={form.notifyOnFailedAttempt}
              onChange={(checked) =>
                setForm((prev) => ({ ...prev, notifyOnFailedAttempt: checked }))
              }
            />
          </div>
        </div>
      </section>

      {/* Section 3: System & Marketing */}
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
        <div className="border-b border-slate-100 pb-4">
          <div className="flex items-center gap-2">
            <ShieldAlert className="size-5 text-indigo-600" />
            <h2 className="text-base font-semibold text-slate-900">System & Additional Alerts</h2>
          </div>
          <p className="mt-0.5 text-sm text-slate-500">
            Manage security notifications and promotional communications.
          </p>
        </div>

        <div className="mt-6 space-y-5">
          <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-4">
            <Switch
              label="System Maintenance & Platform Status"
              description="Advance alerts regarding scheduled maintenance and feature upgrades."
              checked={form.systemMaintenanceAlerts}
              onChange={(checked) =>
                setForm((prev) => ({ ...prev, systemMaintenanceAlerts: checked }))
              }
            />
          </div>

          <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-4">
            <Switch
              label="Promotional Offers & Delivery Discounts"
              description="Occasional discount codes, holiday delivery promotions, and product news."
              checked={form.marketingEmails}
              onChange={(checked) => setForm((prev) => ({ ...prev, marketingEmails: checked }))}
            />
          </div>
        </div>
      </section>

      {/* Form Actions */}
      <div className="flex flex-wrap items-center justify-between gap-4 pt-2">
        <Button
          type="button"
          variant="outline"
          onClick={handleReset}
          className="text-slate-600 hover:text-slate-900"
        >
          <RotateCcw className="size-4" />
          Reset to Defaults
        </Button>

        <Button type="submit" className="bg-brand-700 hover:bg-brand-800">
          <Save className="size-4" />
          Save Preferences
        </Button>
      </div>
    </form>
  );
}

