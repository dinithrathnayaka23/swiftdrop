import { ArrowLeft, Check, SearchX, Share2, TriangleAlert } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { MOCK_TRACKING_NUMBERS } from '../../api/mock/mockTrackingApi';
import LoadingState from '../../components/feedback/LoadingState';
import StateView from '../../components/feedback/StateView';
import TrackParcelIllustration from '../../components/illustrations/TrackParcelIllustration';
import TrackingResultCard from '../../components/tracking/TrackingResultCard';
import TrackingSearchForm from '../../components/tracking/TrackingSearchForm';
import Button from '../../components/ui/Button';
import ButtonLink from '../../components/ui/ButtonLink';
import { useParcelTracking } from '../../hooks/useParcelTracking';
import { PATHS } from '../../routes/paths';
import { isMockApiEnabled } from '../../utils/env';

/** /track (search) and /track/:trackingNumber (result). Public, no sign-in needed. */
export default function TrackingPage() {
  const { trackingNumber } = useParams();
  return trackingNumber ? (
    // key: reset state when a different number is tracked
    <TrackingResultView key={trackingNumber} trackingNumber={trackingNumber} />
  ) : (
    <TrackingSearchView />
  );
}

function TrackingSearchView() {
  return (
    <div className="bg-[radial-gradient(#e2e8f0_1px,transparent_1px)] [background-size:22px_22px]">
      <div className="mx-auto flex max-w-2xl flex-col items-center px-4 py-12 text-center sm:py-20">
        <TrackParcelIllustration className="w-64 sm:w-80" />
        <h1 className="mt-6 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
          Track Your Parcel
        </h1>
        <p className="mt-2 text-slate-600">
          Enter your tracking number to get the latest delivery status.
        </p>
        <TrackingSearchForm className="mt-8 w-full" autoFocus />
        {isMockApiEnabled() && (
          <p className="mt-4 text-xs text-slate-500">
            Demo: try {MOCK_TRACKING_NUMBERS.inTransit} or {MOCK_TRACKING_NUMBERS.delivered}
          </p>
        )}
      </div>
    </div>
  );
}

function BackToSearch() {
  return (
    <Link
      to={PATHS.track}
      className="inline-flex items-center gap-1.5 rounded-md text-sm font-medium text-slate-600 hover:text-slate-900"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Track Another Parcel
    </Link>
  );
}

function ShareButton() {
  const [copied, setCopied] = useState(false);

  async function share() {
    const url = window.location.href;
    try {
      if (navigator.share) {
        await navigator.share({ title: 'SwiftDrop parcel tracking', url });
      } else {
        await navigator.clipboard.writeText(url);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch {
      // share sheet dismissed or clipboard unavailable: nothing to do
    }
  }

  return (
    <Button variant="outline" size="sm" onClick={share}>
      {copied ? (
        <Check className="size-4" aria-hidden="true" />
      ) : (
        <Share2 className="size-4" aria-hidden="true" />
      )}
      {copied ? 'Link copied' : 'Share'}
    </Button>
  );
}

function TrackingResultView({ trackingNumber }: { trackingNumber: string }) {
  const tracking = useParcelTracking(trackingNumber);

  return (
    <div className="bg-slate-50">
      <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:py-12">
        <div className="mb-4 flex items-center justify-between gap-3">
          <BackToSearch />
          {tracking.status === 'found' && <ShareButton />}
        </div>

        {tracking.status === 'loading' && (
          <div className="rounded-2xl border border-slate-200 bg-white">
            <LoadingState title="Finding your parcel..." />
          </div>
        )}

        {tracking.status === 'found' && <TrackingResultCard result={tracking.data} />}

        {tracking.status === 'not-found' && (
          <div className="rounded-2xl border border-slate-200 bg-white">
            <StateView
              className="py-14"
              illustration={
                <div className="flex size-28 items-center justify-center rounded-full bg-slate-100">
                  <SearchX className="size-14 text-red-500" strokeWidth={1.6} aria-hidden="true" />
                </div>
              }
              title="Parcel Not Found"
              message={
                <>
                  We couldn&apos;t find any parcel with the tracking number{' '}
                  <span className="font-semibold text-slate-700">
                    &ldquo;{trackingNumber}&rdquo;
                  </span>
                  . Please check the tracking number and try again.
                </>
              }
              actions={<ButtonLink to={PATHS.track}>Try Again</ButtonLink>}
            />
          </div>
        )}

        {tracking.status === 'error' && (
          <div className="rounded-2xl border border-slate-200 bg-white">
            <StateView
              icon={TriangleAlert}
              tone="danger"
              title="Something Went Wrong"
              message={tracking.message}
              actions={
                <Button variant="outline" onClick={tracking.retry}>
                  Try Again
                </Button>
              }
            />
          </div>
        )}
      </div>
    </div>
  );
}
