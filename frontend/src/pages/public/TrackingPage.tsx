import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import PagePlaceholder from '../../components/PagePlaceholder';
import { PATHS } from '../../routes/paths';

/** Public tracking: will call GET /api/v1/public/tracking/{trackingNumber}. */
export default function TrackingPage() {
  const { trackingNumber } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(trackingNumber ?? '');

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const value = input.trim();
    if (value) {
      navigate(`${PATHS.track}/${encodeURIComponent(value)}`);
    }
  }

  return (
    <div className="mx-auto max-w-xl">
      <form onSubmit={onSubmit} className="mb-6 flex flex-col gap-2 sm:flex-row">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Tracking number"
          aria-label="Tracking number"
          className="flex-1 rounded border border-slate-300 px-3 py-2"
        />
        <button
          type="submit"
          className="rounded bg-brand-600 px-4 py-2 text-white hover:bg-brand-700"
        >
          Track
        </button>
      </form>
      <PagePlaceholder
        title={trackingNumber ? `Parcel ${trackingNumber}` : 'Track a parcel'}
        description="Live status and delivery timeline for a parcel."
      />
    </div>
  );
}
