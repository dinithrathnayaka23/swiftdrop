import Alert from '../../../components/ui/Alert';
import type { BulkAssignResult } from '../../../types/dispatch';

interface BulkResultSummaryProps {
  result: BulkAssignResult;
  /** The tracking number to show for a parcel id. */
  trackingOf: (parcelId: string) => string | undefined;
  courierName: string;
}

/** "8 assigned, 2 failed" after a bulk assignment, with each failed parcel and the reason. */
export default function BulkResultSummary({
  result,
  trackingOf,
  courierName,
}: BulkResultSummaryProps) {
  const assigned = result.assigned.length;
  const failed = result.failed.length;
  const title = failed === 0 ? `${assigned} assigned` : `${assigned} assigned, ${failed} failed`;
  return (
    <Alert tone={failed === 0 ? 'success' : 'warning'} title={title}>
      {assigned > 0 && (
        <p>
          {assigned === 1 ? '1 parcel was' : `${assigned} parcels were`} given to {courierName}.
        </p>
      )}
      {failed > 0 && (
        <>
          <p className="mt-1">These parcels were not assigned and stay selected:</p>
          <ul className="mt-1 list-disc space-y-0.5 pl-5" aria-label="Parcels not assigned">
            {result.failed.map((failure) => (
              <li key={failure.parcelId}>
                <span className="font-semibold">
                  #{trackingOf(failure.parcelId) ?? failure.parcelId}
                </span>
                : {failure.reason}
              </li>
            ))}
          </ul>
        </>
      )}
    </Alert>
  );
}
