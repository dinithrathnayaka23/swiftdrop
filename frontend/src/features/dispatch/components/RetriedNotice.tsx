import { Link } from 'react-router';
import Alert from '../../../components/ui/Alert';
import { PATHS } from '../../../routes/paths';
import type { FailedParcelRef } from '../failedActions';

/** After a retry: the parcel is waiting for a courier again, with a link to assign it. */
export default function RetriedNotice({ parcel }: { parcel: FailedParcelRef }) {
  return (
    <Alert tone="success" title="Back in Unassigned">
      #{parcel.trackingNumber} is waiting for a courier again.{' '}
      <Link
        to={`${PATHS.adminUnassigned}?parcelId=${encodeURIComponent(parcel.parcelId)}`}
        className="font-semibold underline"
      >
        Assign a courier
      </Link>
    </Alert>
  );
}
